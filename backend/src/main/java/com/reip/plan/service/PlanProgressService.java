package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.plan.entity.PlanProgressReport;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进度填报：周报登记 / 周汇总 / 实际日期登记
 */
@Service
@RequiredArgsConstructor
public class PlanProgressService {

    private final PlanMappers.ProgressReportMapper reportMapper;
    private final PlanMappers.TaskMapper taskMapper;
    private final PlanTaskService taskService;

    // ==================== 查询 ====================

    public PageResult<PlanProgressReport> page(long current, long size, Long projectId,
                                               String reportWeek, Long taskId) {
        LambdaQueryWrapper<PlanProgressReport> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(PlanProgressReport::getProjectId, projectId);
        if (reportWeek != null && !reportWeek.isBlank()) qw.eq(PlanProgressReport::getReportWeek, reportWeek);
        if (taskId != null) qw.eq(PlanProgressReport::getTaskId, taskId);
        qw.orderByDesc(PlanProgressReport::getId);
        Page<PlanProgressReport> p = reportMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(r -> r.setTaskName(taskName(r.getTaskId())));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 周汇总：按周聚合填报任务数/平均进度，取最近 N 周 */
    public List<Map<String, Object>> weeklySummary(Long projectId, int weeks) {
        if (projectId == null) throw new BizException("请选择项目");
        List<PlanProgressReport> list = reportMapper.selectList(new LambdaQueryWrapper<PlanProgressReport>()
                .eq(PlanProgressReport::getProjectId, projectId).orderByDesc(PlanProgressReport::getId));
        Map<String, List<PlanProgressReport>> byWeek = new LinkedHashMap<>();
        for (PlanProgressReport r : list) {
            String wk = r.getReportWeek() == null ? "未分周" : r.getReportWeek();
            byWeek.computeIfAbsent(wk, k -> new java.util.ArrayList<>()).add(r);
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        byWeek.entrySet().stream()
                .sorted(Map.Entry.<String, List<PlanProgressReport>>comparingByKey(Comparator.reverseOrder()))
                .limit(weeks <= 0 ? 8 : weeks)
                .forEach(e -> {
                    List<PlanProgressReport> rs = e.getValue();
                    BigDecimal avg = rs.stream().map(r -> PlanSupport.nvl(r.getProgressPct()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(rs.size()), 2, java.math.RoundingMode.HALF_UP);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reportWeek", e.getKey());
                    m.put("taskCount", rs.size());
                    m.put("avgProgress", avg);
                    rows.add(m);
                });
        return rows;
    }

    // ==================== 填报登记 ====================

    /** 登记周报并回写任务进度/实际日期（R2 前置依赖校验由 taskService 执行） */
    @Transactional
    public Long record(PlanProgressReport r) {
        if (r == null) throw new BizException("缺少填报内容");
        if (r.getTaskId() == null) throw new BizException("请选择任务");
        PlanTask t = taskService.require(r.getTaskId());
        if (r.getProjectId() == null) r.setProjectId(t.getProjectId());
        boolean startTouched = r.getActualStart() != null && !r.getActualStart().equals(t.getActualStart());
        // 先回写任务进度/预警/事件
        taskService.reportProgress(r.getTaskId(), r.getProgressPct(),
                r.getActualStart(), r.getActualEnd(), startTouched);
        // 再落周报
        if (r.getReportWeek() == null || r.getReportWeek().isBlank()) r.setReportWeek(currentWeek());
        r.setId(null);
        r.setStatus("SUBMITTED");
        reportMapper.insert(r);
        return r.getId();
    }

    /** 仅登记实际开始/完成日期（不动进度） */
    @Transactional
    public void registerActualDates(Long taskId, LocalDate actualStart, LocalDate actualEnd) {
        if (actualStart == null && actualEnd == null) throw new BizException("请提供实际日期");
        PlanTask t = taskService.require(taskId);
        boolean startTouched = actualStart != null && !actualStart.equals(t.getActualStart());
        taskService.reportProgress(taskId, PlanSupport.nvl(t.getProgressPct()), actualStart, actualEnd, startTouched);
    }

    // ==================== 私有 ====================

    private String taskName(Long taskId) {
        PlanTask t = taskId == null ? null : taskMapper.selectById(taskId);
        return t == null ? null : t.getTaskName();
    }

    private static String currentWeek() {
        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return now.get(wf.weekBasedYear()) + "-W" + String.format("%02d", now.get(wf.weekOfWeekBasedYear()));
    }
}
