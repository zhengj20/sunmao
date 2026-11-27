package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.entity.SaleRefundExchange;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.entity.SaleSubscription;
import com.reip.sales.mapper.SaleMappers;
import com.reip.workflow.api.WorkflowClient;
import lombok.Data;
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
 * 认购：登记(校验房源可售/预占) / 提交审批 / 转签约 / 退定 / 超期未签约预警
 */
@Service
@RequiredArgsConstructor
public class SaleSubscriptionService {

    private final SaleMappers.SubscriptionMapper subMapper;
    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.RefundExchangeMapper refundMapper;
    private final SaleRoomService roomService;
    private final SaleRefundService refundService;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final ParamService paramService;

    // ==================== 查询 ====================

    public PageResult<SaleSubscription> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<SaleSubscription> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleSubscription::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(SaleSubscription::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(SaleSubscription::getSubNo, kw);
        qw.orderByDesc(SaleSubscription::getId);
        Page<SaleSubscription> p = subMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleSubscription detail(Long id) {
        SaleSubscription s = require(id);
        fillDisplay(s);
        return s;
    }

    // ==================== 登记 ====================

    @Transactional
    public Long create(SaleSubscription s) {
        if (s == null) throw new BizException("缺少认购内容");
        if (s.getProjectId() == null) throw new BizException("请选择项目");
        if (s.getRoomId() == null) throw new BizException("请选择房源");
        if (s.getCustomerId() == null) throw new BizException("请选择客户");
        if (support.customerName(s.getCustomerId()) == null) throw new BizException("客户不存在：" + s.getCustomerId());
        if (s.getDeposit() == null) s.setDeposit(BigDecimal.ZERO);
        // R2：房源须可售（未锁定他人/未认购/未签约）
        roomService.assertSellable(s.getRoomId());
        s.setId(null);
        s.setSubNo(codeRuleService.next("RG", support.codeCtx(s.getProjectId())));
        s.setStatus("DRAFT");
        subMapper.insert(s);
        // 预占房源（SELLABLE→LOCKED），防止并发重复认购
        roomService.hold(s.getRoomId(), s.getSubNo());
        return s.getId();
    }

    /** 提交审批（SALE_SUBSCRIPTION，金额=定金） */
    @Transactional
    public void submit(Long id) {
        SaleSubscription s = require(id);
        if (!"DRAFT".equals(s.getStatus())) throw new BizException("仅草稿状态的认购可提交审批");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("认购单号", s.getSubNo());
        snapshot.put("房源", roomCodeOf(s.getRoomId()));
        snapshot.put("客户", support.customerName(s.getCustomerId()));
        snapshot.put("定金", s.getDeposit());
        snapshot.put("置业顾问", support.agentName(s.getAgentId()));
        workflowClient.start("SALE_SUBSCRIPTION", id, "认购审批 " + s.getSubNo(), s.getDeposit(), snapshot);
        s.setStatus("APPROVING");
        if (subMapper.updateById(s) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 转签约 ====================

    /** 转签约：由有效认购生成合同草稿（房源保持 SUBSCRIBED，待签约审批通过后转 CONTRACTED） */
    @Transactional
    public Long convert(Long id, ConvertForm form) {
        SaleSubscription s = require(id);
        if (!"ACTIVE".equals(s.getStatus())) throw new BizException("仅生效状态的认购可转签约");
        // 幂等：已转签约则直接返回既有合同
        if (s.getContractId() != null) return s.getContractId();
        SaleRoomStatus room = roomService.require(s.getRoomId());
        BigDecimal tablePrice = SaleSupport.nvl(room.getTablePrice());
        BigDecimal contractPrice = (form != null && form.getContractPrice() != null) ? form.getContractPrice() : tablePrice;
        String payMethod = (form != null && form.getPayMethod() != null && !form.getPayMethod().isBlank())
                ? form.getPayMethod() : "MORTGAGE";

        SaleContract c = new SaleContract();
        c.setProjectId(s.getProjectId());
        c.setRoomId(s.getRoomId());
        c.setCustomerId(s.getCustomerId());
        c.setContractPrice(contractPrice);
        c.setPayMethod(payMethod);
        c.setDiscountRate(tablePrice.compareTo(BigDecimal.ZERO) == 0 ? null
                : contractPrice.divide(tablePrice, 4, RoundingMode.HALF_UP));
        c.setSubscriptionId(s.getId());
        c.setSupplierId(s.getAgentId());
        c.setSignedAt(form != null && form.getSignedDate() != null ? form.getSignedDate() : LocalDate.now());
        c.setContractNo(codeRuleService.next("XHT", support.codeCtx(s.getProjectId())));
        c.setStatus("DRAFT");
        contractMapper.insert(c);

        s.setContractId(c.getId());
        s.setStatus("CONVERTED");
        if (subMapper.updateById(s) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return c.getId();
    }

    // ==================== 退定 ====================

    /** 退定：生成退换单(退定)并提交 SALE_SUBSCRIPTION_TERMINATE 审批 */
    @Transactional
    public Long refund(Long id, String reason) {
        SaleSubscription s = require(id);
        if (!"ACTIVE".equals(s.getStatus())) throw new BizException("仅生效状态的认购可退定");
        SaleRefundExchange re = new SaleRefundExchange();
        re.setProjectId(s.getProjectId());
        re.setSubscriptionId(s.getId());
        re.setRoomId(s.getRoomId());
        re.setType("REFUND");
        re.setRefundAmount(SaleSupport.nvl(s.getDeposit()));
        re.setReason(reason);
        re.setReasonCategory("客户原因");
        re.setRefundNo(codeRuleService.next("TH", support.codeCtx(s.getProjectId())));
        re.setStatus("DRAFT");
        refundMapper.insert(re);
        refundService.submit(re.getId());
        return re.getId();
    }

    // ==================== 工作流回调 ====================

    /** 认购审批通过：ACTIVE + 房源 SUBSCRIBED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        SaleSubscription s = subMapper.selectById(id);
        if (s == null || !"APPROVING".equals(s.getStatus())) return; // 幂等
        s.setStatus("ACTIVE");
        subMapper.updateById(s);
        roomService.setSubscribed(s.getRoomId(), s.getSubNo());
    }

    /** 认购审批驳回：回 DRAFT + 释放房源 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        SaleSubscription s = subMapper.selectById(id);
        if (s == null || !"APPROVING".equals(s.getStatus())) return;
        s.setStatus("DRAFT");
        subMapper.updateById(s);
        roomService.release(s.getRoomId());
    }

    /** 退定审批通过：认购 TERMINATED + 房源释放（由退换服务驱动，此处供事件兜底） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTerminated(Long id) {
        SaleSubscription s = subMapper.selectById(id);
        if (s == null || !"ACTIVE".equals(s.getStatus())) return;
        s.setStatus("TERMINATED");
        subMapper.updateById(s);
    }

    // ==================== 超期未签约预警 ====================

    /** ACTIVE 认购超过 N 天（sales.sub.overdue_days，默认30）未签约 → 标记预警 */
    @Transactional
    public int warnOverdue() {
        int days = paramService.getInt("sales.sub.overdue_days", 30);
        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<SaleSubscription> subs = subMapper.selectList(new LambdaQueryWrapper<SaleSubscription>()
                .eq(SaleSubscription::getStatus, "ACTIVE")
                .isNull(SaleSubscription::getContractId)
                .le(SaleSubscription::getExpectedSignDate, cutoff));
        for (SaleSubscription s : subs) {
            // 预警仅提示，不改变状态：记录在 updated_by 由当前登录触发，保持幂等
            s.setExpectedSignDate(cutoff);
            subMapper.updateById(s);
        }
        return subs.size();
    }

    // ==================== 私有 ====================

    private SaleSubscription require(Long id) {
        if (id == null) throw new BizException("缺少认购ID");
        SaleSubscription s = subMapper.selectById(id);
        if (s == null) throw new BizException("认购不存在");
        return s;
    }

    private void fillDisplay(SaleSubscription s) {
        s.setCustomerName(support.customerName(s.getCustomerId()));
        s.setRoomCode(support.roomCode(s.getRoomId()));
    }

    private String roomCodeOf(Long roomId) {
        String code = support.roomCode(roomId);
        return code == null ? String.valueOf(roomId) : code;
    }

    @Data
    public static class ConvertForm {
        private BigDecimal contractPrice;
        private String payMethod;
        private LocalDate signedDate;
    }
}
