package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.PlanProgressReport;
import com.reip.plan.service.PlanProgressService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 进度填报：周报登记 / 周汇总 / 实际日期登记
 */
@RestController
@RequestMapping("/api/plan/progress")
@RequiredArgsConstructor
public class PlanProgressController {

    private final PlanProgressService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanProgressReport>> page(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "20") long size,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String reportWeek,
                                                        @RequestParam(required = false) Long taskId) {
        return Result.ok(svc.page(current, size, projectId, reportWeek, taskId));
    }

    /** 登记周报（回写任务进度/预警/事件） */
    @PostMapping
    public Result<Long> record(@RequestBody PlanProgressReport r) {
        return Result.ok(svc.record(r));
    }

    /** 周汇总 */
    @GetMapping("/weekly")
    public Result<List<Map<String, Object>>> weekly(@RequestParam Long projectId,
                                                    @RequestParam(defaultValue = "8") int weeks) {
        return Result.ok(svc.weeklySummary(projectId, weeks));
    }

    /** 仅登记实际日期 */
    @PostMapping("/register-dates")
    public Result<Void> registerDates(@RequestBody DatesForm form) {
        svc.registerActualDates(form.getTaskId(), form.getActualStart(), form.getActualEnd());
        return Result.ok();
    }

    @Data
    public static class DatesForm {
        private Long taskId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate actualStart;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate actualEnd;
    }
}
