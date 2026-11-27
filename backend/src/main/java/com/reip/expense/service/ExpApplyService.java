package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpApply;
import com.reip.expense.entity.ExpBudget;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 费用事前申请：登记 / 提交占用预算(R1) / 审批回写
 */
@Service
@RequiredArgsConstructor
public class ExpApplyService {

    private final ExpenseMappers.ApplyMapper applyMapper;
    private final ExpenseMappers.BudgetMapper budgetMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final ParamService paramService;

    public PageResult<ExpApply> page(long current, long size, Long deptId, String type, String status, String kw) {
        LambdaQueryWrapper<ExpApply> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(ExpApply::getDeptId, deptId);
        if (type != null && !type.isBlank()) qw.eq(ExpApply::getType, type);
        if (status != null && !status.isBlank()) qw.eq(ExpApply::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(ExpApply::getApplyNo, kw);
        qw.orderByDesc(ExpApply::getId);
        Page<ExpApply> p = applyMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpApply detail(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(ExpApply a) {
        if (a == null) throw new BizException("缺少申请内容");
        if (a.getType() == null || a.getType().isBlank()) throw new BizException("请选择类型(TRAVEL/HOST/PROCURE)");
        if (a.getDeptId() == null) throw new BizException("请选择部门");
        ExpSupport.requireAmount(a.getAmount(), "申请金额");
        if (a.getBudgetId() != null && budgetMapper.selectById(a.getBudgetId()) == null)
            throw new BizException("预算不存在：" + a.getBudgetId());
        a.setId(null);
        a.setApplyNo(codeRuleService.next("YSSY", Map.of()));
        if (a.getUserId() == null) a.setUserId(UserContext.require().getUserId());
        if (a.getUserName() == null || a.getUserName().isBlank()) a.setUserName(UserContext.require().getRealName());
        a.setOccupyAmount(BigDecimal.ZERO);
        a.setStatus("DRAFT");
        applyMapper.insert(a);
        return a.getId();
    }

    @Transactional
    public void update(ExpApply a) {
        if (a == null || a.getId() == null) throw new BizException("缺少申请ID");
        ExpApply db = require(a.getId());
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的申请可修改");
        a.setApplyNo(null);
        a.setStatus(db.getStatus());
        if (a.getOccupyAmount() == null) a.setOccupyAmount(db.getOccupyAmount());
        if (applyMapper.updateById(a) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ExpApply db = require(id);
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的申请可删除");
        applyMapper.deleteById(id);
    }

    /**
     * 提交审批（bizType=EXP_BUDGET）：
     *  R1 预算占用硬校验（余额不足默认阻断）→ 占用预算 → 发起审批
     */
    @Transactional
    public void submit(Long id) {
        ExpApply a = require(id);
        if (!"DRAFT".equals(a.getStatus()) && !"REJECTED".equals(a.getStatus()))
            throw new BizException("当前状态不可提交申请");
        BigDecimal amount = ExpSupport.nvl(a.getAmount());
        if (amount.signum() <= 0) throw new BizException("请填写申请金额");
        // 行程勾稽：出差类申请必须关联预算
        if ("TRAVEL".equals(a.getType()) && a.getBudgetId() == null)
            throw new BizException("出差申请须关联预算");
        // R1 预算占用
        if (a.getBudgetId() != null && paramService.getBool("exp.budget.strict", true)) {
            ExpBudget b = budgetMapper.selectById(a.getBudgetId());
            if (b == null) throw new BizException("预算不存在");
            if (ExpSupport.nvl(b.getBalance()).compareTo(amount) < 0)
                throw new BizException("超预算：预算余额 " + b.getBalance() + " 不足以覆盖申请金额 " + amount);
            int n = budgetMapper.occupy(a.getBudgetId(), amount);
            if (n == 0) throw new BizException("超预算：预算余额不足，无法占用（R1）");
            a.setOccupyAmount(amount);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("申请单号", a.getApplyNo());
        snapshot.put("类型", a.getType());
        snapshot.put("部门", a.getDeptId());
        snapshot.put("金额", amount);
        workflowClient.start("EXP_BUDGET", id, "费用申请 " + a.getApplyNo(), amount, snapshot);
        a.setStatus("APPROVING");
        if (applyMapper.updateById(a) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 工作流回调 ====================

    /** 申请审批通过：ACTIVE（占用保持） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        ExpApply a = applyMapper.selectById(id);
        if (a == null || !"APPROVING".equals(a.getStatus())) return; // 幂等
        a.setStatus("ACTIVE");
        applyMapper.updateById(a);
    }

    /** 申请审批驳回：回 DRAFT/REJECTED + 释放预算占用 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        ExpApply a = applyMapper.selectById(id);
        if (a == null || !"APPROVING".equals(a.getStatus())) return; // 幂等
        a.setStatus("REJECTED");
        applyMapper.updateById(a);
        if (a.getBudgetId() != null && ExpSupport.nvl(a.getOccupyAmount()).signum() > 0) {
            budgetMapper.releaseOccupy(a.getBudgetId(), a.getOccupyAmount());
        }
    }

    private ExpApply require(Long id) {
        if (id == null) throw new BizException("缺少申请ID");
        ExpApply a = applyMapper.selectById(id);
        if (a == null) throw new BizException("申请不存在");
        return a;
    }
}
