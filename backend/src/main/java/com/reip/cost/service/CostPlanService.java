package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostPlan;
import com.reip.cost.entity.CostPlanItem;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 合约规划：规划 CRUD / 明细 / 余量 / 提交审批 / 三联穿透
 */
@Service
@RequiredArgsConstructor
public class CostPlanService {

    private final CostMappers.PlanMapper planMapper;
    private final CostMappers.PlanItemMapper itemMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final CostSupport support;

    // ==================== 查询 ====================

    public PageResult<CostPlan> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<CostPlan> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostPlan::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(CostPlan::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(CostPlan::getPlanCode, kw).or().like(CostPlan::getName, kw));
        qw.orderByDesc(CostPlan::getId);
        Page<CostPlan> p = planMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostPlan detail(Long id) {
        CostPlan plan = require(id);
        plan.setItems(items(id));
        return plan;
    }

    /** 明细列表（余量=规划金额-已签约-在途，实时计算） */
    public List<CostPlanItem> items(Long planId) {
        require(planId);
        List<CostPlanItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostPlanItem>()
                .eq(CostPlanItem::getPlanId, planId).orderByAsc(CostPlanItem::getAccountId));
        items.forEach(this::fillRemaining);
        return items;
    }

    // ==================== 规划 CRUD ====================

    @Transactional
    public Long create(CostPlan plan, List<CostPlanItem> items) {
        if (plan == null) throw new BizException("缺少表单内容");
        if (plan.getProjectId() == null) throw new BizException("请选择项目");
        Map<String, String> ctx = support.codeCtx(plan.getProjectId()); // 校验项目存在
        plan.setId(null);
        plan.setPlanCode(codeRuleService.next("HYGH", ctx));
        plan.setStatus("DRAFT");
        planMapper.insert(plan);
        if (items != null) {
            for (CostPlanItem item : items) {
                normalizeItem(item, plan);
                item.setId(null);
                item.setPlanId(plan.getId());
                itemMapper.insert(item);
            }
        }
        return plan.getId();
    }

    /** 修改：APPROVED 后修改回 DRAFT 需重新提交审批 */
    @Transactional
    public void update(CostPlan plan) {
        if (plan == null || plan.getId() == null) throw new BizException("缺少规划ID");
        CostPlan db = require(plan.getId());
        if ("APPROVING".equals(db.getStatus())) throw new BizException("审批中的合约规划禁止修改");
        plan.setPlanCode(null);
        // 已审批通过的修改后回 DRAFT 重新走审批
        plan.setStatus("APPROVED".equals(db.getStatus()) ? "DRAFT" : db.getStatus());
        if (planMapper.updateById(plan) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostPlan db = require(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的合约规划可删除");
        planMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<CostPlanItem>().eq(CostPlanItem::getPlanId, id));
    }

    // ==================== 明细 CRUD ====================

    @Transactional
    public Long addItem(Long planId, CostPlanItem item) {
        CostPlan plan = requireEditable(planId);
        if (item == null) throw new BizException("缺少明细内容");
        normalizeItem(item, plan);
        item.setId(null);
        item.setPlanId(planId);
        itemMapper.insert(item);
        resetToDraftIfApproved(plan);
        return item.getId();
    }

    @Transactional
    public void updateItem(Long planId, CostPlanItem item) {
        if (item == null || item.getId() == null) throw new BizException("缺少明细ID");
        CostPlanItem db = itemMapper.selectById(item.getId());
        if (db == null || !db.getPlanId().equals(planId)) throw new BizException("规划明细不存在");
        CostPlan plan = requireEditable(planId);
        normalizeItem(item, plan);
        item.setPlanId(planId);
        if (itemMapper.updateById(item) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        resetToDraftIfApproved(plan);
    }

    @Transactional
    public void deleteItem(Long planId, Long itemId) {
        CostPlan plan = requireEditable(planId);
        int deleted = itemMapper.delete(new LambdaQueryWrapper<CostPlanItem>()
                .eq(CostPlanItem::getId, itemId).eq(CostPlanItem::getPlanId, planId));
        if (deleted == 0) throw new BizException("规划明细不存在");
        resetToDraftIfApproved(plan);
    }

    /** 三联穿透：规划明细下挂的合同列表 */
    public List<CostContract> drill(Long planId, Long itemId) {
        require(planId);
        CostPlanItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getPlanId().equals(planId)) throw new BizException("规划明细不存在");
        return contractMapper.selectList(new LambdaQueryWrapper<CostContract>()
                .eq(CostContract::getPlanItemId, itemId).orderByDesc(CostContract::getId));
    }

    // ==================== 审批 ====================

    /** 提交审批（bizType=COST_PLAN，金额=明细规划金额合计） */
    @Transactional
    public void submit(Long id) {
        CostPlan plan = require(id);
        if (!"DRAFT".equals(plan.getStatus())) throw new BizException("仅草稿状态的合约规划可提交审批");
        List<CostPlanItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostPlanItem>()
                .eq(CostPlanItem::getPlanId, id));
        BigDecimal totalPlanned = items.stream().map(i -> CostSupport.nvl(i.getPlannedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planCode", plan.getPlanCode());
        snapshot.put("name", plan.getName());
        snapshot.put("totalPlanned", totalPlanned);
        snapshot.put("projectId", plan.getProjectId());
        workflowClient.start("COST_PLAN", id, "合约规划 " + plan.getPlanCode(), totalPlanned, snapshot);
        CostPlan up = new CostPlan();
        up.setId(id);
        up.setVersion(plan.getVersion());
        up.setStatus("APPROVING");
        if (planMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 提交规划调整审批（已生效的规划修改后走调整审批） */
    @Transactional
    public void submitAdjustment(Long id) {
        CostPlan plan = require(id);
        if (!"DRAFT".equals(plan.getStatus())) throw new BizException("仅草稿状态的合约规划可提交调整审批");
        List<CostPlanItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostPlanItem>()
                .eq(CostPlanItem::getPlanId, id));
        BigDecimal totalPlanned = items.stream().map(i -> CostSupport.nvl(i.getPlannedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planCode", plan.getPlanCode());
        snapshot.put("name", plan.getName());
        snapshot.put("totalPlanned", totalPlanned);
        snapshot.put("projectId", plan.getProjectId());
        snapshot.put("adjustment", true);
        workflowClient.start("COST_PLAN", id, "合约规划调整 " + plan.getPlanCode(), totalPlanned, snapshot);
        CostPlan up = new CostPlan();
        up.setId(id);
        up.setVersion(plan.getVersion());
        up.setStatus("APPROVING");
        if (planMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 工作流回调：审批通过 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostPlan plan = planMapper.selectById(id);
        if (plan == null || !"APPROVING".equals(plan.getStatus())) return; // 幂等
        CostPlan up = new CostPlan();
        up.setId(id);
        up.setVersion(plan.getVersion());
        up.setStatus("APPROVED");
        planMapper.updateById(up);
    }

    /** 工作流回调：驳回（退回 DRAFT） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostPlan plan = planMapper.selectById(id);
        if (plan == null || !"APPROVING".equals(plan.getStatus())) return;
        CostPlan up = new CostPlan();
        up.setId(id);
        up.setVersion(plan.getVersion());
        up.setStatus("DRAFT");
        planMapper.updateById(up);
    }

    // ==================== 私有方法 ====================

    private CostPlan require(Long id) {
        if (id == null) throw new BizException("缺少规划ID");
        CostPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BizException("合约规划不存在");
        return plan;
    }

    private CostPlan requireEditable(Long planId) {
        CostPlan plan = require(planId);
        if ("APPROVING".equals(plan.getStatus())) throw new BizException("审批中的合约规划禁止修改明细");
        return plan;
    }

    /** 已审批通过的规划明细变动后回 DRAFT 重新走审批 */
    private void resetToDraftIfApproved(CostPlan plan) {
        if (!"APPROVED".equals(plan.getStatus())) return;
        CostPlan up = new CostPlan();
        up.setId(plan.getId());
        up.setVersion(plan.getVersion());
        up.setStatus("DRAFT");
        planMapper.updateById(up);
    }

    private void normalizeItem(CostPlanItem item, CostPlan plan) {
        CostMappers.AccountRef acc = support.account(item.getAccountId());
        item.setAccountCode(acc.getCode());
        item.setAccountName(acc.getName());
        if (item.getPlannedAmount() == null) throw new BizException("请填写规划金额");
        item.setProjectId(plan.getProjectId());
    }

    /** 余量=规划金额-已签约占用-在途占用 */
    private void fillRemaining(CostPlanItem item) {
        BigDecimal remaining = CostSupport.nvl(item.getPlannedAmount())
                .subtract(CostSupport.nvl(item.getContractedAmount()))
                .subtract(CostSupport.nvl(item.getPendingAmount()));
        item.setRemainingAmount(remaining);
    }
}
