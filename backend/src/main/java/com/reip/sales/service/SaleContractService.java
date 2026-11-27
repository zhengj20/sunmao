package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.entity.SalePaymentPlan;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.mapper.SaleMappers;
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
 * 销售合同：签约登记 / 折扣刚性 R3 校验 / 提交审批 / 退房 / 审批回写 + 事件发布
 */
@Service
@RequiredArgsConstructor
public class SaleContractService {

    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.PaymentPlanMapper paymentPlanMapper;
    private final SaleMappers.ApprovalLookupMapper approvalLookup;
    private final SaleRoomService roomService;
    private final SalePaymentService paymentService;
    private final SaleRefundService refundService;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;
    private final ParamService paramService;

    // ==================== 查询 ====================

    public PageResult<SaleContract> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<SaleContract> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleContract::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(SaleContract::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(SaleContract::getContractNo, kw);
        qw.orderByDesc(SaleContract::getId);
        Page<SaleContract> p = contractMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleContract detail(Long id) {
        SaleContract c = require(id);
        fillDisplay(c);
        c.setCollectionRate(collectionRate(id));
        return c;
    }

    // ==================== 签约登记 ====================

    @Transactional
    public Long create(SaleContract c) {
        if (c == null) throw new BizException("缺少合同内容");
        if (c.getProjectId() == null) throw new BizException("请选择项目");
        if (c.getRoomId() == null) throw new BizException("请选择房源");
        if (c.getCustomerId() == null) throw new BizException("请选择客户");
        if (support.customerName(c.getCustomerId()) == null) throw new BizException("客户不存在：" + c.getCustomerId());
        if (c.getContractPrice() == null || c.getContractPrice().signum() <= 0) throw new BizException("请填写成交总价");
        if (c.getPayMethod() == null || c.getPayMethod().isBlank()) throw new BizException("请选择付款方式(ONE_TIME/MORTGAGE/INSTALLMENT)");
        // 房源须可售或已认购（可签约）
        SaleRoomStatus room = roomService.require(c.getRoomId());
        String st = room.getStatus();
        if (!"SELLABLE".equals(st) && !"LOCKED".equals(st) && !"SUBSCRIBED".equals(st))
            throw new BizException("该房源当前不可签约（状态：" + st + "）");
        c.setId(null);
        BigDecimal tablePrice = SaleSupport.nvl(room.getTablePrice());
        c.setDiscountRate(tablePrice.signum() == 0 ? null
                : c.getContractPrice().divide(tablePrice, 4, RoundingMode.HALF_UP));
        if (c.getSignedAt() == null) c.setSignedAt(LocalDate.now());
        c.setContractNo(codeRuleService.next("XHT", support.codeCtx(c.getProjectId())));
        c.setStatus("DRAFT");
        contractMapper.insert(c);
        return c.getId();
    }

    // ==================== 折扣刚性 R3 ====================

    /** R3：成交折扣低于置业顾问权限上限时，必须已存在批准的特价房审批，否则阻断提交 */
    private void checkDiscountStrict(SaleContract c) {
        if (!paramService.getBool("sales.discount.strict", true)) return;
        BigDecimal rate = c.getDiscountRate();
        if (rate == null) return;
        BigDecimal agentMax = approvalLookup.selectAgentMaxRate();
        if (agentMax == null) return;
        // rate < agentMax 表示折扣比顾问权限更深（下探更多），须特价审批
        if (rate.compareTo(agentMax) < 0) {
            long approved = approvalLookup.countApprovedSpecialPrice(c.getId());
            if (approved <= 0)
                throw new BizException("成交折扣低于置业顾问权限上限(" + agentMax + ")，须先完成特价房审批");
        }
    }

    /** 发起特价房审批（SALE_SPECIAL_PRICE），批准后解锁超权限折扣提交 */
    @Transactional
    public void submitSpecialPrice(Long id) {
        SaleContract c = require(id);
        if (!"DRAFT".equals(c.getStatus())) throw new BizException("仅草稿状态的合同可发起特价审批");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("合同号", c.getContractNo());
        snapshot.put("成交折扣率", c.getDiscountRate());
        snapshot.put("成交总价", c.getContractPrice());
        workflowClient.start("SALE_SPECIAL_PRICE", id, "特价房审批 " + c.getContractNo(),
                c.getContractPrice(), snapshot);
    }

    // ==================== 提交审批 ====================

    /** 提交审批（SALE_CONTRACT，金额=成交总价），先做 R3 折扣刚性校验 */
    @Transactional
    public void submit(Long id) {
        SaleContract c = require(id);
        if (!"DRAFT".equals(c.getStatus())) throw new BizException("仅草稿状态的合同可提交审批");
        checkDiscountStrict(c); // R3 硬校验：超权限折扣须特价审批
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("合同号", c.getContractNo());
        snapshot.put("房源", support.roomCode(c.getRoomId()));
        snapshot.put("客户", support.customerName(c.getCustomerId()));
        snapshot.put("成交总价", c.getContractPrice());
        snapshot.put("成交折扣率", c.getDiscountRate());
        snapshot.put("付款方式", c.getPayMethod());
        workflowClient.start("SALE_CONTRACT", id, "签约审批 " + c.getContractNo(),
                c.getContractPrice(), snapshot);
        c.setStatus("APPROVING");
        if (contractMapper.updateById(c) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 退房 ====================

    /** 退房：生成退换单(退房)并提交 SALE_REFUND 审批 */
    @Transactional
    public Long refund(Long id, String reason) {
        SaleContract c = require(id);
        if (!"ACTIVE".equals(c.getStatus()) && !"FILED".equals(c.getStatus()))
            throw new BizException("仅生效/网签合同可退房，当前：" + c.getStatus());
        return refundService.createFromContract(c, reason);
    }

    // ==================== 工作流回调 ====================

    /** 签约审批通过：ACTIVE + 房源 CONTRACTED + 生成应收计划 + 发布 sales.contract.signed */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        SaleContract c = contractMapper.selectById(id);
        if (c == null || !"APPROVING".equals(c.getStatus())) return; // 幂等
        c.setStatus("ACTIVE");
        contractMapper.updateById(c);
        roomService.setContracted(c.getRoomId(), c.getContractNo());
        paymentService.generatePlans(id); // 按付款方式生成应收
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", c.getProjectId());
        payload.put("contractId", c.getId());
        payload.put("roomId", c.getRoomId());
        payload.put("customerId", c.getCustomerId());
        payload.put("contractPrice", c.getContractPrice());
        payload.put("discountRate", c.getDiscountRate());
        payload.put("payMethod", c.getPayMethod());
        eventPublisher.publish("sales.contract.signed", "SALE_CONTRACT", c.getId(), payload);
    }

    /** 签约审批驳回：回 DRAFT + 房源回退（认购来源→SUBSCRIBED，否则→SELLABLE） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        SaleContract c = contractMapper.selectById(id);
        if (c == null || !"APPROVING".equals(c.getStatus())) return;
        c.setStatus("DRAFT");
        contractMapper.updateById(c);
        SaleRoomStatus room = roomService.require(c.getRoomId());
        if ("SUBSCRIBED".equals(room.getStatus())) {
            // 保持 SUBSCRIBED（认购仍有效，可重新签约或退定）
        } else {
            roomService.release(c.getRoomId());
        }
    }

    // ==================== 私有 ====================

    private SaleContract require(Long id) {
        if (id == null) throw new BizException("缺少合同ID");
        SaleContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException("合同不存在");
        return c;
    }

    private void fillDisplay(SaleContract c) {
        c.setCustomerName(support.customerName(c.getCustomerId()));
        c.setRoomCode(support.roomCode(c.getRoomId()));
    }

    /** 回款率% = 已收 / 应收 */
    private BigDecimal collectionRate(Long contractId) {
        List<SalePaymentPlan> plans = paymentPlanMapper.selectList(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId));
        BigDecimal total = plans.stream().map(p -> SaleSupport.nvl(p.getPlanAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = plans.stream().map(p -> SaleSupport.nvl(p.getReceivedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return BigDecimal.ZERO;
        return received.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }
}
