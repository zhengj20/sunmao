package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.plan.entity.PlanAdjust;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划调整：R1里程碑须填延期原因归类 / 提交走 PLAN_ADJUST 审批 / 审批回写刷新基线 / 版本对比
 */
@Service
@RequiredArgsConstructor
public class PlanAdjustService {

    private final PlanMappers.AdjustMapper adjustMapper;
    private final PlanMappers.TaskMapper taskMapper;
    private final PlanTaskService taskService;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final ParamService paramService;
    private final PlanSupport support;

    // ==================== 查询 ====================

    public PageResult<PlanAdjust> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<PlanAdjust> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(PlanAdjust::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(PlanAdjust::getAdjustNo, kw);
        if (projectId != null) qw.in(PlanAdjust::getTaskId, taskIdsOfProject(projectId));
        qw.orderByDesc(PlanAdjust::getId);
        Page<PlanAdjust> p = adjustMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PlanAdjust detail(Long id) {
        PlanAdjust a = require(id);
        fillDisplay(a);
        a.setTaskName(taskNameOf(a.getTaskId()));
        return a;
    }

    // ==================== 调整申请 ====================

    /** 登记调整单（R1：里程碑须填延期原因归类） */
    @Transactional
    public Long create(PlanAdjust a) {
        if (a == null) throw new BizException("缺少调整内容");
        if (a.getTaskId() == null) throw new BizException("请选择任务");
        if (a.getReason() == null || a.getReason().isBlank()) throw new BizException("请填写调整原因");
        if (a.getNewStart() == null && a.getNewEnd() == null) throw new BizException("请填写新的计划时间");
        PlanTask t = taskService.require(a.getTaskId());
        // R1 里程碑锁定：里程碑调整须填延期原因归类
        if (support.isMilestone(t) && paramService.getBool("plan.milestone.lock", true)
                && (a.getReasonCategory() == null || a.getReasonCategory().isBlank()))
            throw new BizException("里程碑调整须填写延期原因归类(R1)");
        a.setId(null);
        a.setOldStart(t.getPlanStart());
        a.setOldEnd(t.getPlanEnd());
        a.setAdjustNo(codeRuleService.next("PLJH", support.codeCtx(t.getProjectId())));
        a.setStatus("DRAFT");
        adjustMapper.insert(a);
        return a.getId();
    }

    /** 提交审批：里程碑须已填归类 → 发起 PLAN_ADJUST */
    @Transactional
    public void submit(Long id) {
        PlanAdjust a = require(id);
        if (!"DRAFT".equals(a.getStatus()) && !"REJECTED".equals(a.getStatus()))
            throw new BizException("当前状态不可提交审批");
        if (a.getReasonCategory() == null || a.getReasonCategory().isBlank())
            throw new BizException("请填写延期原因归类");
        if (a.getNewStart() == null && a.getNewEnd() == null)
            throw new BizException("请填写新的计划时间");
        PlanTask t = taskService.require(a.getTaskId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("任务", t.getTaskName());
        snapshot.put("是否里程碑", support.isMilestone(t) ? "是" : "否");
        snapshot.put("原计划", t.getPlanStart() + " ~ " + t.getPlanEnd());
        snapshot.put("新计划", a.getNewStart() + " ~ " + a.getNewEnd());
        snapshot.put("原因", a.getReason());
        snapshot.put("归类", a.getReasonCategory());
        workflowClient.start("PLAN_ADJUST", id, "计划调整 " + a.getAdjustNo(), null, snapshot);
        a.setStatus("APPROVING");
        if (adjustMapper.updateById(a) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 工作流回调 ====================

    /** 审批通过：调整单 APPROVED + 更新任务时间并刷新基线 + 重算预警 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        PlanAdjust a = adjustMapper.selectById(id);
        if (a == null || !"APPROVING".equals(a.getStatus())) return; // 幂等守卫
        a.setStatus("APPROVED");
        adjustMapper.updateById(a);
        PlanTask t = taskMapper.selectById(a.getTaskId());
        if (t == null) return;
        if (a.getNewStart() != null) t.setPlanStart(a.getNewStart());
        if (a.getNewEnd() != null) t.setPlanEnd(a.getNewEnd());
        // 刷新基线（调整生效后基线同步为新计划）
        t.setBaselineStart(t.getPlanStart());
        t.setBaselineEnd(t.getPlanEnd());
        t.setAlertStatus(PlanSupport.alertColor(t, LocalDate.now(), paramService.getInt("plan.alert.yellow_days", 14)));
        taskMapper.updateById(t);
    }

    /** 审批驳回：回 DRAFT 可再编辑 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        PlanAdjust a = adjustMapper.selectById(id);
        if (a == null || !"APPROVING".equals(a.getStatus())) return; // 幂等守卫
        a.setStatus("REJECTED");
        adjustMapper.updateById(a);
    }

    // ==================== 私有 ====================

    private List<Long> taskIdsOfProject(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PlanTask>().eq(PlanTask::getProjectId, projectId))
                .stream().map(PlanTask::getId).toList();
    }

    private String taskNameOf(Long taskId) {
        PlanTask t = taskId == null ? null : taskMapper.selectById(taskId);
        return t == null ? null : t.getTaskName();
    }

    private void fillDisplay(PlanAdjust a) {
        a.setTaskName(taskNameOf(a.getTaskId()));
    }

    private PlanAdjust require(Long id) {
        if (id == null) throw new BizException("缺少调整单ID");
        PlanAdjust a = adjustMapper.selectById(id);
        if (a == null) throw new BizException("调整单不存在");
        return a;
    }
}
