package com.reip.plan.controller;

import com.reip.common.result.Result;
import com.reip.plan.service.PlanOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 计划总览：KPI / 地铁图 / 专项进度 / 施工进度 / 指标统计与预警（一次聚合）
 */
@RestController
@RequestMapping("/api/plan/overview")
@RequiredArgsConstructor
public class PlanOverviewController {

    private final PlanOverviewService svc;

    /** 计划总览聚合数据 */
    @GetMapping
    public Result<Map<String, Object>> overview(@RequestParam Long projectId) {
        return Result.ok(svc.overview(projectId));
    }
}
