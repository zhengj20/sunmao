package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.entity.SaleRefundExchange;
import com.reip.sales.entity.SaleSubscription;
import com.reip.sales.mapper.SaleMappers;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 退换：退定 / 退房 审批发起 + 工作流回写（释放房源 + 发布领域事件）
 * 退定 bizType=SALE_SUBSCRIPTION_TERMINATE；退房 bizType=SALE_REFUND
 */
@Service
@RequiredArgsConstructor
public class SaleRefundService {

    private final SaleMappers.RefundExchangeMapper refundMapper;
    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.SubscriptionMapper subMapper;
    private final SaleRoomService roomService;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;

    // ==================== 查询 ====================

    public PageResult<SaleRefundExchange> page(long current, long size, Long projectId, String status, String type) {
        LambdaQueryWrapper<SaleRefundExchange> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleRefundExchange::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(SaleRefundExchange::getStatus, status);
        if (type != null && !type.isBlank()) qw.eq(SaleRefundExchange::getType, type);
        qw.orderByDesc(SaleRefundExchange::getId);
        Page<SaleRefundExchange> p = refundMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleRefundExchange detail(Long id) {
        SaleRefundExchange r = require(id);
        fillDisplay(r);
        return r;
    }

    // ==================== 退换单登记 ====================

    /** 登记退换单（DRAFT）：退房(contract_id) 或 退定(subscription_id)，后续再 submit 发起审批 */
    @Transactional
    public Long create(SaleRefundExchange re) {
        if (re == null) throw new BizException("缺少退换单内容");
        if (re.getContractId() == null && re.getSubscriptionId() == null)
            throw new BizException("退房须关联合同，退定须关联认购");
        if (re.getContractId() != null) {
            SaleContract c = contractMapper.selectById(re.getContractId());
            if (c == null) throw new BizException("合同不存在");
            if (!"ACTIVE".equals(c.getStatus()) && !"FILED".equals(c.getStatus()))
                throw new BizException("仅生效/网签合同可退房，当前：" + c.getStatus());
            re.setProjectId(c.getProjectId());
            re.setRoomId(c.getRoomId());
            re.setType("REFUND");
            if (re.getRefundAmount() == null) re.setRefundAmount(c.getContractPrice());
        } else {
            SaleSubscription s = subMapper.selectById(re.getSubscriptionId());
            if (s == null) throw new BizException("认购不存在");
            if (!"ACTIVE".equals(s.getStatus())) throw new BizException("仅生效状态的认购可退定");
            re.setProjectId(s.getProjectId());
            re.setRoomId(s.getRoomId());
            re.setType("REFUND");
            if (re.getRefundAmount() == null) re.setRefundAmount(SaleSupport.nvl(s.getDeposit()));
        }
        re.setId(null);
        if (re.getReason() == null || re.getReason().isBlank()) throw new BizException("请填写退换原因");
        re.setRefundNo(codeRuleService.next("TH", support.codeCtx(re.getProjectId())));
        re.setStatus("DRAFT");
        refundMapper.insert(re);
        return re.getId();
    }

    // ==================== 退房（由合同触发） ====================

    /** 退房：生成退换单并提交 SALE_REFUND 审批 */
    @Transactional
    public Long createFromContract(SaleContract c, String reason) {
        SaleRefundExchange re = new SaleRefundExchange();
        re.setContractId(c.getId());
        re.setReason(reason == null || reason.isBlank() ? "客户退房" : reason);
        re.setReasonCategory("客户原因");
        Long id = create(re);
        submit(id);
        return id;
    }

    // ==================== 提交审批 ====================

    /** 提交审批：按来源路由 bizType（退房 SALE_REFUND / 退定 SALE_SUBSCRIPTION_TERMINATE） */
    @Transactional
    public void submit(Long refundId) {
        SaleRefundExchange re = require(refundId);
        if (!"DRAFT".equals(re.getStatus())) throw new BizException("仅草稿状态的退换单可提交审批");
        String bizType;
        String title;
        if (re.getContractId() != null) {
            bizType = "SALE_REFUND";
            title = "退房审批 " + re.getRefundNo();
        } else {
            bizType = "SALE_SUBSCRIPTION_TERMINATE";
            title = "退定审批 " + re.getRefundNo();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("退换单号", re.getRefundNo());
        snapshot.put("类型", re.getType());
        snapshot.put("退还金额", re.getRefundAmount());
        snapshot.put("原因", re.getReason());
        BigDecimal amount = re.getRefundAmount() == null ? BigDecimal.ZERO : re.getRefundAmount();
        workflowClient.start(bizType, refundId, title, amount, snapshot);
        re.setStatus("APPROVING");
        if (refundMapper.updateById(re) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 工作流回调 ====================

    /** 退换审批通过：终态 APPROVED + 释放房源 + 发布事件（退房→合同 REFUNDED / 退定→认购 TERMINATED） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long refundId) {
        SaleRefundExchange re = refundMapper.selectById(refundId);
        if (re == null || !"APPROVING".equals(re.getStatus())) return; // 幂等
        re.setStatus("APPROVED");
        refundMapper.updateById(re);

        if (re.getContractId() != null) {
            SaleContract c = contractMapper.selectById(re.getContractId());
            if (c != null && !"REFUNDED".equals(c.getStatus())) {
                c.setStatus("REFUNDED");
                contractMapper.updateById(c);
            }
            roomService.release(re.getRoomId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", re.getProjectId());
            payload.put("contractId", re.getContractId());
            payload.put("roomId", re.getRoomId());
            payload.put("refundAmount", re.getRefundAmount());
            payload.put("refundId", re.getId());
            eventPublisher.publish("sales.contract.refunded", "SALE_REFUND", re.getId(), payload);
        } else if (re.getSubscriptionId() != null) {
            SaleSubscription s = subMapper.selectById(re.getSubscriptionId());
            if (s != null && !"TERMINATED".equals(s.getStatus())) {
                s.setStatus("TERMINATED");
                subMapper.updateById(s);
            }
            roomService.release(re.getRoomId());
        }
    }

    /** 退换审批驳回：回 REJECTED（房源保持原状态） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long refundId) {
        SaleRefundExchange re = refundMapper.selectById(refundId);
        if (re == null || !"APPROVING".equals(re.getStatus())) return;
        re.setStatus("REJECTED");
        refundMapper.updateById(re);
    }

    // ==================== 私有 ====================

    private SaleRefundExchange require(Long id) {
        if (id == null) throw new BizException("缺少退换单ID");
        SaleRefundExchange r = refundMapper.selectById(id);
        if (r == null) throw new BizException("退换单不存在");
        return r;
    }

    private void fillDisplay(SaleRefundExchange r) {
        if (r.getContractId() != null) {
            SaleContract c = contractMapper.selectById(r.getContractId());
            r.setContractNo(c == null ? null : c.getContractNo());
        }
        r.setRoomCode(support.roomCode(r.getRoomId()));
    }
}
