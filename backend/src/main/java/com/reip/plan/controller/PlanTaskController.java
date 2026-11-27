package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.service.PlanTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 主项/专项计划：模板生成WBS / 进度填报 / R1里程碑锁定 / R2前置依赖
 */
@RestController
@RequestMapping("/api/plan/tasks")
@RequiredArgsConstructor
public class PlanTaskController {

    private final PlanTaskService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanTask>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String specialty,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String alertStatus,
                                             @RequestParam(required = false) Integer isMilestone,
                                             @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, specialty, status, alertStatus, isMilestone, kw));
    }

    /** 专项计划：按专业过滤 */
    @GetMapping("/specialty/page")
    public Result<PageResult<PlanTask>> specialtyPage(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam Long projectId,
                                                      @RequestParam String specialty) {
        return Result.ok(svc.page(current, size, projectId, specialty, null, null, null, null));
    }

    @GetMapping("/list")
    public Result<List<PlanTask>> list(@RequestParam Long projectId) {
        return Result.ok(svc.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public Result<PlanTask> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 从模板生成项目WBS */
    @PostMapping("/generate")
    public Result<Long> generate(@RequestParam Long templateId,
                                 @RequestParam Long projectId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return Result.ok(svc.generate(templateId, projectId, startDate));
    }

    /** 进度填报（更新进度/实际日期 + 预警重算 + R2前置依赖） */
    @PostMapping("/{id}/report-progress")
    public Result<PlanTask> reportProgress(@PathVariable Long id, @RequestBody ProgressForm form) {
        return Result.ok(svc.reportProgress(id, form.getProgressPct(),
                form.getActualStart(), form.getActualEnd(), form.getActualStart() != null));
    }

    /** 直接改计划时间（R1：里程碑阻断） */
    @PutMapping("/{id}/schedule")
    public Result<Void> updateSchedule(@PathVariable Long id, @RequestBody ScheduleForm form) {
        svc.updateSchedule(id, form.getPlanStart(), form.getPlanEnd());
        return Result.ok();
    }

    @Data
    public static class ProgressForm {
        private BigDecimal progressPct;
        private LocalDate actualStart;
        private LocalDate actualEnd;
    }

    @Data
    public static class ScheduleForm {
        private LocalDate planStart;
        private LocalDate planEnd;
    }
}
