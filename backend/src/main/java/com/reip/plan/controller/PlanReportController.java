package com.reip.plan.controller;

import com.reip.common.result.Result;
import com.reip.plan.service.PlanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 计划报表：甘特图 / 里程碑达成月报 / 各单位执行排名
 */
@RestController
@RequestMapping("/api/plan/reports")
@RequiredArgsConstructor
public class PlanReportController {

    private final PlanReportService svc;

    /** 甘特图数据（节点/计划区间/进度/预警色） */
    @GetMapping("/gantt")
    public Result<List<Map<String, Object>>> gantt(@RequestParam Long projectId) {
        return Result.ok(svc.gantt(projectId));
    }

    /** 里程碑达成月报 */
    @GetMapping("/milestone")
    public Result<Map<String, Object>> milestone(@RequestParam Long projectId) {
        return Result.ok(svc.milestone(projectId));
    }

    /** 各单位执行排名（红黄绿统计 + 完成率） */
    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> ranking(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.ranking(projectId));
    }
}
