package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.budget.service.BudControlService;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostPaymentApply;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 付款申请：提交硬校验（累计/发票/预付比例）/ 审批 / 实付登记
 */
@Service
@RequiredArgsConstructor
public class CostPaymentService {

    /** 预付款比例上限（占签约金额） */
    private static final BigDecimal PREPAY_RATE_LIMIT = new BigDecimal("0.30");

    private final CostMappers.PaymentApplyMapper paymentMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;
    private final ParamService paramService;
    private final CostSupport support;
    private final CostChangeService changeService;
    private final BudControlService budControlService;

    // ==================== 查询 ====================

    public PageResult<CostPaymentApply> page(long current, long size, Long projectId, Long contractId,
                                             String paymentType, String status, String kw) {
        LambdaQueryWrapper<CostPaymentApply> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostPaymentApply::getProjectId, projectId);
        if (contractId != null) qw.eq(CostPaymentApply::getContractId, contractId);
        if (paymentType != null && !paymentType.isBlank()) qw.eq(CostPaymentApply::getPaymentType, paymentType);
        if (status != null && !status.isBlank()) qw.eq(CostPaymentApply::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(CostPaymentApply::getPaymentCode, kw);
        qw.orderByDesc(CostPaymentApply::getId);
        Page<CostPaymentApply> p = paymentMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostPaymentApply detail(Long id) {
        return require(id);
    }

    // ==================== CRUD ====================

    @Transactional
    public Long create(CostPaymentApply payment) {
        if (payment == null) throw new BizException("缺少表单内容");
        if (payment.getContractId() == null) throw new BizException("请选择关联合同");
        CostContract contract = contractMapper.selectById(payment.getContractId());
        if (contract == null) throw new BizException("关联合同不存在");
        if (payment.getPaymentType() == null || payment.getPaymentType().isBlank())
            throw new BizException("请选择付款类型");
        if (payment.getApplyAmount() == null) throw new BizException("请填写申请金额");
        payment.setProjectId(contract.getProjectId()); // 项目以合同为准
        payment.setId(null);
        payment.setPaymentCode(codeRuleService.next("FK", support.codeCtx(contract.getProjectId())));
        payment.setAppliedAmount(approvedSum(payment.getContractId())); // 申请时点累计已审
        payment.setStatus("DRAFT");
        if (payment.getInvoiceAmount() == null) payment.setInvoiceAmount(BigDecimal.ZERO);
        paymentMapper.insert(payment);
        return payment.getId();
    }

    @Transactional
    public void update(CostPaymentApply payment) {
        if (payment == null || payment.getId() == null) throw new BizException("缺少付款单ID");
        CostPaymentApply db = require(payment.getId());
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的付款申请可修改");
        payment.setPaymentCode(null);
        payment.setProjectId(db.getProjectId());
        payment.setStatus(db.getStatus());
        if (paymentMapper.updateById(payment) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostPaymentApply db = require(id);
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的付款申请可删除");
        paymentMapper.deleteById(id);
    }

    // ==================== 提交（硬控制） ====================

    /** 提交审批（bizType=COST_PAYMENT）：a累计 b发票 c预付比例 三重硬校验 */
    @Transactional
    public void submit(Long id) {
        CostPaymentApply p = require(id);
        if (!"DRAFT".equals(p.getStatus()) && !"REJECTED".equals(p.getStatus()))
            throw new BizException("当前状态不可提交付款申请");
        CostContract contract = contractMapper.selectById(p.getContractId());
        if (contract == null) throw new BizException("关联合同不存在");
        if (!"EFFECTIVE".equals(contract.getStatus()) && !"SETTLED".equals(contract.getStatus()))
            throw new BizException("合同未生效，不能提交付款申请");
        BigDecimal apply = CostSupport.nvl(p.getApplyAmount());
        if (apply.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写申请金额");
        // a) 累计校验：历史已审金额+本次 ≤ 签约额+确认变更累计
        BigDecimal ceiling = CostSupport.nvl(contract.getSignedAmount())
                .add(changeService.confirmedNetByContract(p.getContractId()));
        if (approvedSum(p.getContractId()).add(apply).compareTo(ceiling) > 0)
            throw new BizException("累计付款超合同额与确认变更之和");
        // b) 发票校验：需先登记发票（参数可关）
        if (paramService.getBool("cost.pay.invoice_required", true)
                && CostSupport.nvl(p.getInvoiceAmount()).compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("需先登记发票");
        // c) 预付款比例 ≤ 签约额30%
        if ("PREPAY".equals(p.getPaymentType())) {
            BigDecimal cap = CostSupport.nvl(contract.getSignedAmount())
                    .multiply(PREPAY_RATE_LIMIT).setScale(2, RoundingMode.HALF_UP);
            if (prepaySum(p.getContractId()).add(apply).compareTo(cap) > 0)
                throw new BizException("预付款比例超过签约额30%上限");
        }
        // d) B3 全面预算月度管控：按合同科目分摊校验当月成本预算余额（刚性阻断/预警放行）
        List<String> budWarnings = budControlService.checkCostPayment(contract.getProjectId(), p.getContractId(), apply);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentCode", p.getPaymentCode());
        snapshot.put("paymentType", p.getPaymentType());
        snapshot.put("contractId", p.getContractId());
        snapshot.put("applyAmount", apply);
        if (!budWarnings.isEmpty()) snapshot.put("budgetWarning", String.join("；", budWarnings));
        workflowClient.start("COST_PAYMENT", id, "付款申请 " + p.getPaymentCode(), apply, snapshot);
        CostPaymentApply up = new CostPaymentApply();
        up.setId(id);
        up.setVersion(p.getVersion());
        up.setStatus("APPROVING");
        if (paymentMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 工作流回调 / 实付 ====================

    /** 工作流回调：通过 → APPROVED 并发布付款审批事件 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostPaymentApply p = paymentMapper.selectById(id);
        if (p == null || !"APPROVING".equals(p.getStatus())) return; // 幂等
        CostPaymentApply up = new CostPaymentApply();
        up.setId(id);
        up.setVersion(p.getVersion());
        up.setStatus("APPROVED");
        up.setApprovedAmount(CostSupport.nvl(p.getApplyAmount()));
        paymentMapper.updateById(up);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", p.getProjectId());
        payload.put("contractId", p.getContractId());
        payload.put("paymentId", p.getId());
        payload.put("amount", CostSupport.nvl(p.getApplyAmount()));
        eventPublisher.publish("cost.payment.approved", "COST_PAYMENT", p.getId(), payload);
    }

    /** 工作流回调：驳回 → REJECTED（可改后重提） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostPaymentApply p = paymentMapper.selectById(id);
        if (p == null || !"APPROVING".equals(p.getStatus())) return;
        CostPaymentApply up = new CostPaymentApply();
        up.setId(id);
        up.setVersion(p.getVersion());
        up.setStatus("REJECTED");
        paymentMapper.updateById(up);
    }

    /** 实付登记：APPROVED→PAID，发布实付事件 */
    @Transactional
    public void markPaid(Long id, BigDecimal paidAmount, LocalDate paidDate) {
        CostPaymentApply p = require(id);
        if (!"APPROVED".equals(p.getStatus())) throw new BizException("仅审批通过的付款申请可登记实付");
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写实付金额");
        CostPaymentApply up = new CostPaymentApply();
        up.setId(id);
        up.setVersion(p.getVersion());
        up.setStatus("PAID");
        up.setPaidAmount(paidAmount);
        up.setPaidDate(paidDate == null ? LocalDate.now() : paidDate);
        if (paymentMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", p.getProjectId());
        payload.put("contractId", p.getContractId());
        payload.put("paymentId", p.getId());
        payload.put("amount", paidAmount);
        payload.put("paidDate", up.getPaidDate());
        eventPublisher.publish("cost.payment.paid", "COST_PAYMENT", p.getId(), payload);
    }

    // ==================== 私有方法 ====================

    private CostPaymentApply require(Long id) {
        if (id == null) throw new BizException("缺少付款单ID");
        CostPaymentApply p = paymentMapper.selectById(id);
        if (p == null) throw new BizException("付款申请不存在");
        return p;
    }

    /** 合同历史已审金额（含实付） */
    private BigDecimal approvedSum(Long contractId) {
        return paymentMapper.selectList(new LambdaQueryWrapper<CostPaymentApply>()
                        .eq(CostPaymentApply::getContractId, contractId)
                        .in(CostPaymentApply::getStatus, "APPROVED", "PAID")).stream()
                .map(p -> CostSupport.nvl(p.getApplyAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 合同累计预付款（含在途申请外的已审部分） */
    private BigDecimal prepaySum(Long contractId) {
        return paymentMapper.selectList(new LambdaQueryWrapper<CostPaymentApply>()
                        .eq(CostPaymentApply::getContractId, contractId)
                        .eq(CostPaymentApply::getPaymentType, "PREPAY")
                        .in(CostPaymentApply::getStatus, "APPROVED", "PAID")).stream()
                .map(p -> CostSupport.nvl(p.getApplyAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
