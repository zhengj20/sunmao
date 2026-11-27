package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostChange;
import com.reip.cost.entity.CostContract;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 变更管理：两阶段（估价→确认）
 * ESTIMATING →(submit-estimate)→ ESTIMATE_APPROVING →(通过)→ CONFIRMING
 * →(submit-confirm)→ APPROVING →(通过)→ CONFIRMED；驳回 → REJECTED 可改后重提
 */
@Service
@RequiredArgsConstructor
public class CostChangeService {

    /** 可编辑状态（驳回单可改后按阶段重提） */
    private static final Set<String> EDITABLE = Set.of("ESTIMATING", "CONFIRMING", "REJECTED");

    private final CostMappers.ChangeMapper changeMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;
    private final CostSupport support;

    // ==================== 查询 ====================

    public PageResult<CostChange> page(long current, long size, Long projectId, Long contractId,
                                       String changeType, String status, String kw) {
        LambdaQueryWrapper<CostChange> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostChange::getProjectId, projectId);
        if (contractId != null) qw.eq(CostChange::getContractId, contractId);
        if (changeType != null && !changeType.isBlank()) qw.eq(CostChange::getChangeType, changeType);
        if (status != null && !status.isBlank()) qw.eq(CostChange::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(CostChange::getTitle, kw).or().like(CostChange::getChangeCode, kw));
        qw.orderByDesc(CostChange::getId);
        Page<CostChange> p = changeMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostChange detail(Long id) {
        return require(id);
    }

    // ==================== CRUD ====================

    @Transactional
    public Long create(CostChange change) {
        if (change == null) throw new BizException("缺少表单内容");
        if (change.getTitle() == null || change.getTitle().isBlank()) throw new BizException("请填写变更标题");
        int requireContract = change.getRequireContract() == null ? 1 : change.getRequireContract();
        change.setRequireContract(requireContract);
        if (requireContract == 1) {
            if (change.getContractId() == null) throw new BizException("请选择关联合同");
            CostContract contract = contractMapper.selectById(change.getContractId());
            if (contract == null) throw new BizException("关联合同不存在");
            change.setProjectId(contract.getProjectId()); // 项目以合同为准
        } else {
            if (change.getAccountId() == null) throw new BizException("无合同变更必须选择成本科目");
            if (change.getProjectId() == null) throw new BizException("请选择项目");
        }
        change.setId(null);
        change.setChangeCode(codeRuleService.next("BG", support.codeCtx(change.getProjectId())));
        change.setStatus("ESTIMATING");
        if (change.getDeductAmount() == null) change.setDeductAmount(BigDecimal.ZERO);
        changeMapper.insert(change);
        return change.getId();
    }

    @Transactional
    public void update(CostChange change) {
        if (change == null || change.getId() == null) throw new BizException("缺少变更单ID");
        CostChange db = require(change.getId());
        if (!EDITABLE.contains(db.getStatus())) throw new BizException("当前状态不可修改");
        change.setChangeCode(null);
        change.setProjectId(db.getProjectId());
        change.setStatus(db.getStatus());
        if (changeMapper.updateById(change) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostChange db = require(id);
        if (!EDITABLE.contains(db.getStatus())) throw new BizException("当前状态不可删除");
        changeMapper.deleteById(id);
    }

    // ==================== 两阶段提交 ====================

    /** 一阶段：估价提交（金额=estimate_amount） */
    @Transactional
    public void submitEstimate(Long id) {
        CostChange c = require(id);
        boolean resubmit = "REJECTED".equals(c.getStatus()) && c.getConfirmedAmount() == null;
        if (!"ESTIMATING".equals(c.getStatus()) && !resubmit) throw new BizException("当前状态不可提交估价审批");
        if (CostSupport.nvl(c.getEstimateAmount()).compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("请先填写估价金额");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("changeCode", c.getChangeCode());
        snapshot.put("title", c.getTitle());
        snapshot.put("changeType", c.getChangeType());
        snapshot.put("estimateAmount", c.getEstimateAmount());
        workflowClient.start("COST_CHANGE", id, "变更估价 " + c.getChangeCode(),
                c.getEstimateAmount(), snapshot);
        updateStatus(id, c, "ESTIMATE_APPROVING");
    }

    /** 二阶段：确认提交（金额=confirmed_amount） */
    @Transactional
    public void submitConfirm(Long id, BigDecimal confirmedAmount, BigDecimal deductAmount) {
        CostChange c = require(id);
        boolean resubmit = "REJECTED".equals(c.getStatus()) && c.getConfirmedAmount() != null;
        if (!"CONFIRMING".equals(c.getStatus()) && !resubmit) throw new BizException("当前状态不可提交确认审批");
        // 更新确认金额和扣款金额
        if (confirmedAmount != null) {
            CostChange up = new CostChange();
            up.setId(id);
            up.setVersion(c.getVersion());
            up.setConfirmedAmount(confirmedAmount);
            if (deductAmount != null) up.setDeductAmount(deductAmount);
            if (changeMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
            c.setConfirmedAmount(confirmedAmount);
            if (deductAmount != null) c.setDeductAmount(deductAmount);
        }
        if (CostSupport.nvl(c.getConfirmedAmount()).compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("请先填写确认金额");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("changeCode", c.getChangeCode());
        snapshot.put("title", c.getTitle());
        snapshot.put("changeType", c.getChangeType());
        snapshot.put("confirmedAmount", c.getConfirmedAmount());
        workflowClient.start("COST_CHANGE", id, "变更确认 " + c.getChangeCode(),
                c.getConfirmedAmount(), snapshot);
        updateStatus(id, c, "APPROVING");
    }

    // ==================== 工作流回调 ====================

    /** 工作流回调：通过（估价通过→待确认并固化初值；确认通过→已确认并发布事件） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostChange c = changeMapper.selectById(id);
        if (c == null) return;
        if ("ESTIMATE_APPROVING".equals(c.getStatus())) {
            // 估价金额固化为确认金额初值
            CostChange up = new CostChange();
            up.setId(id);
            up.setVersion(c.getVersion());
            up.setStatus("CONFIRMING");
            up.setConfirmedAmount(CostSupport.nvl(c.getEstimateAmount()));
            changeMapper.updateById(up);
        } else if ("APPROVING".equals(c.getStatus())) {
            CostChange up = new CostChange();
            up.setId(id);
            up.setVersion(c.getVersion());
            up.setStatus("CONFIRMED");
            changeMapper.updateById(up);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", c.getProjectId());
            payload.put("contractId", c.getContractId());
            payload.put("changeId", c.getId());
            payload.put("amount", CostSupport.nvl(c.getConfirmedAmount()));
            payload.put("deductAmount", CostSupport.nvl(c.getDeductAmount()));
            eventPublisher.publish("cost.change.confirmed", "COST_CHANGE", c.getId(), payload);
        }
    }

    /** 工作流回调：驳回（→REJECTED，按 confirmed_amount 是否有值判断重提阶段） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostChange c = changeMapper.selectById(id);
        if (c == null) return;
        if (!"ESTIMATE_APPROVING".equals(c.getStatus()) && !"APPROVING".equals(c.getStatus())) return;
        updateStatus(id, c, "REJECTED");
    }

    // ==================== 供同模块引用 ====================

    /** 合同已确认变更净额累计（确认金额-扣款） */
    public BigDecimal confirmedNetByContract(Long contractId) {
        List<CostChange> changes = changeMapper.selectList(new LambdaQueryWrapper<CostChange>()
                .eq(CostChange::getContractId, contractId).eq(CostChange::getStatus, "CONFIRMED"));
        return changes.stream()
                .map(c -> CostSupport.nvl(c.getConfirmedAmount()).subtract(CostSupport.nvl(c.getDeductAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 私有方法 ====================

    private CostChange require(Long id) {
        if (id == null) throw new BizException("缺少变更单ID");
        CostChange c = changeMapper.selectById(id);
        if (c == null) throw new BizException("变更单不存在");
        return c;
    }

    private void updateStatus(Long id, CostChange db, String status) {
        CostChange up = new CostChange();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus(status);
        if (changeMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }
}
