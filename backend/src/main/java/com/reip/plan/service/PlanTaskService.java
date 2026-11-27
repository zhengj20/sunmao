package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.UserContext;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.plan.entity.PlanNodeLib;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目计划任务（WBS）：模板生成 / 进度填报 / 预警重算 / 里程碑锁定R1 / 前置依赖R2
 */
@Service
@RequiredArgsConstructor
public class PlanTaskService {

    private final PlanMappers.TaskMapper taskMapper;
    private final PlanMappers.NodeLibMapper nodeLibMapper;
    private final PlanMappers.TemplateMapper templateMapper;
    private final PlanSupport support;
    private final EventPublisher eventPublisher;
    private final ParamService paramService;

    // ==================== 查询 ====================

    public PageResult<PlanTask> page(long current, long size, Long projectId, String specialty,
                                     String status, String alertStatus, Integer isMilestone, String kw) {
        LambdaQueryWrapper<PlanTask> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(PlanTask::getProjectId, projectId);
        if (specialty != null && !specialty.isBlank()) qw.eq(PlanTask::getSpecialty, specialty);
        if (status != null && !status.isBlank()) qw.eq(PlanTask::getStatus, status);
        if (alertStatus != null && !alertStatus.isBlank()) qw.eq(PlanTask::getAlertStatus, alertStatus);
        if (isMilestone != null) qw.eq(PlanTask::getIsMilestone, isMilestone);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(PlanTask::getTaskName, kw).or().like(PlanTask::getTaskCode, kw));
        qw.orderByAsc(PlanTask::getId);
        Page<PlanTask> p = taskMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<PlanTask> listByProject(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        return taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId).orderByAsc(PlanTask::getId));
    }

    public PlanTask detail(Long id) {
        return require(id);
    }

    // ==================== 从模板生成 WBS ====================

    /** 从模板节点库生成项目 WBS（里程碑/计划时间/责任人） */
    @Transactional
    public long generate(Long templateId, Long projectId, LocalDate startDate) {
        if (templateId == null) throw new BizException("请选择模板");
        if (projectId == null) throw new BizException("请选择项目");
        if (templateMapper.selectById(templateId) == null) throw new BizException("模板不存在");
        Long exists = taskMapper.selectCount(new LambdaQueryWrapper<PlanTask>().eq(PlanTask::getProjectId, projectId));
        if (exists != null && exists > 0) throw new BizException("该项目已存在计划任务，请勿重复生成");
        List<PlanNodeLib> nodes = nodeLibMapper.selectList(new LambdaQueryWrapper<PlanNodeLib>()
                .eq(PlanNodeLib::getTemplateId, templateId).orderByAsc(PlanNodeLib::getSortNo));
        if (nodes.isEmpty()) throw new BizException("模板无节点，无法生成WBS");
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        String projectCode = support.codeCtx(projectId).get("projectCode");
        Long uid = UserContext.require().getUserId();
        String uname = UserContext.require().getRealName();
        int seq = 0;
        for (PlanNodeLib n : nodes) {
            seq++;
            PlanTask t = new PlanTask();
            t.setProjectId(projectId);
            t.setParentId(0L);
            t.setTaskCode(projectCode + "-" + seq);
            t.setTaskName(n.getNodeName());
            t.setLevel(n.getLevel());
            t.setIsMilestone("里程碑".equals(n.getLevel()) ? 1 : 0);
            t.setSpecialty(n.getRespLine());
            LocalDate ps = start;
            LocalDate pe = start.plusDays(n.getBaseDuration() == null ? 30 : n.getBaseDuration());
            t.setPlanStart(ps);
            t.setPlanEnd(pe);
            t.setBaselineStart(ps);
            t.setBaselineEnd(pe);
            t.setProgressPct(BigDecimal.ZERO);
            t.setRespUserId(uid);
            t.setRespUserName(uname);
            t.setAlertStatus("GREEN");
            t.setStatus("PLANNED");
            taskMapper.insert(t);
        }
        return seq;
    }

    // ==================== 进度填报 / 预警 ====================

    /**
     * 进度填报：更新进度/实际日期/状态 + 预警重算 + 前置依赖R2 + 发布事件
     * 里程碑完成时发布 plan.milestone.reached
     */
    @Transactional
    public PlanTask reportProgress(Long taskId, BigDecimal progressPct,
                                   LocalDate actualStart, LocalDate actualEnd, boolean startTouched) {
        PlanTask t = require(taskId);
        if (progressPct == null) throw new BizException("请填写完成进度");
        if (progressPct.compareTo(BigDecimal.ZERO) < 0 || progressPct.compareTo(new BigDecimal("100")) > 0)
            throw new BizException("完成进度须在 0~100 之间");
        // R2 前置依赖：后置实际开始不得早于前置完成
        if (startTouched && actualStart != null) checkPredecessors(t, actualStart);

        String prevStatus = t.getStatus();
        if (progressPct.compareTo(new BigDecimal("100")) >= 0) {
            t.setStatus("COMPLETED");
            if (t.getActualEnd() == null) t.setActualEnd(actualEnd == null ? LocalDate.now() : actualEnd);
        } else {
            t.setStatus("IN_PROGRESS");
        }
        if (actualStart != null) t.setActualStart(actualStart);
        if (actualEnd != null && t.getActualEnd() == null) t.setActualEnd(actualEnd);
        t.setProgressPct(progressPct);
        t.setAlertStatus(recomputeColor(t, LocalDate.now()));
        if (taskMapper.updateById(t) == 0) throw new BizException("数据已被他人修改，请刷新后重试");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", t.getId());
        payload.put("taskName", t.getTaskName());
        payload.put("projectId", t.getProjectId());
        payload.put("progressPct", progressPct);
        payload.put("alertStatus", t.getAlertStatus());
        payload.put("status", t.getStatus());
        eventPublisher.publish("plan.progress.updated", "PLAN_TASK", t.getId(), payload);

        boolean reached = "COMPLETED".equals(t.getStatus()) && !"COMPLETED".equals(prevStatus)
                && support.isMilestone(t);
        if (reached) {
            Map<String, Object> mp = new LinkedHashMap<>(payload);
            mp.put("milestone", t.getTaskName());
            eventPublisher.publish("plan.milestone.reached", "PLAN_MILESTONE", t.getId(), mp);
        }
        return t;
    }

    /** 预警重算（返回新颜色并落库） */
    @Transactional
    public void recomputeAlert(Long taskId) {
        PlanTask t = require(taskId);
        String color = recomputeColor(t, LocalDate.now());
        if (!color.equals(t.getAlertStatus())) {
            t.setAlertStatus(color);
            if ("RED".equals(color) && !"DELAYED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                t.setStatus("DELAYED");
            taskMapper.updateById(t);
        }
    }

    /**
     * R1 里程碑锁定：里程碑节点不可直接改计划时间，只能走计划调整
     */
    @Transactional
    public void updateSchedule(Long taskId, LocalDate planStart, LocalDate planEnd) {
        PlanTask t = require(taskId);
        if (planStart == null && planEnd == null) throw new BizException("请选择新的计划时间");
        if (support.isMilestone(t) && paramService.getBool("plan.milestone.lock", true))
            throw new BizException("里程碑节点[" + t.getTaskName() + "]不可直接修改时间，须走计划调整(R1)");
        if (planStart != null) t.setPlanStart(planStart);
        if (planEnd != null) t.setPlanEnd(planEnd);
        if (taskMapper.updateById(t) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 私有 ====================

    private String recomputeColor(PlanTask t, LocalDate today) {
        int yellow = paramService.getInt("plan.alert.yellow_days", 14);
        return PlanSupport.alertColor(t, today, yellow);
    }

    private void checkPredecessors(PlanTask t, LocalDate actualStart) {
        for (Long preId : PlanSupport.preIds(t.getPreTaskIds())) {
            PlanTask pre = taskMapper.selectById(preId);
            if (pre == null) continue;
            if (pre.getActualEnd() == null)
                throw new BizException("前置任务[" + pre.getTaskName() + "]未完成，不能开始(R2)");
            if (pre.getActualEnd().isAfter(actualStart))
                throw new BizException("前置任务[" + pre.getTaskName() + "]完成时间晚于本任务开始(R2)");
        }
    }

    public PlanTask require(Long id) {
        if (id == null) throw new BizException("缺少任务ID");
        PlanTask t = taskMapper.selectById(id);
        if (t == null) throw new BizException("计划任务不存在");
        return t;
    }
}
