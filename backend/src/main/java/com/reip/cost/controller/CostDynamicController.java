package com.reip.cost.controller;

import com.reip.common.result.Result;
import com.reip.cost.entity.CostDynamicSnapshot;
import com.reip.cost.service.CostDynamicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动态成本：重算快照/最新快照/月度趋势
 */
@RestController
@RequestMapping("/api/cost/dynamic")
@RequiredArgsConstructor
public class CostDynamicController {

    private final CostDynamicService svc;

    /** 重算并落当月快照（幂等） */
    @PostMapping("/refresh")
    public Result<CostDynamicSnapshot> refresh(@RequestParam Long projectId) {
        return Result.ok(svc.refresh(projectId));
    }

    /** 最新快照+历史月度列表 */
    @GetMapping
    public Result<Map<String, Object>> latest(@RequestParam Long projectId) {
        return Result.ok(svc.latest(projectId));
    }

    /** 月度趋势 */
    @GetMapping("/history")
    public Result<List<CostDynamicSnapshot>> history(@RequestParam Long projectId) {
        return Result.ok(svc.history(projectId));
    }

    /** 三算对比：目标成本 vs 动态成本 vs 实际发生成本 */
    @GetMapping("/three-way-compare")
    public Result<Map<String, Object>> threeWayCompare(@RequestParam Long projectId) {
        return Result.ok(svc.threeWayCompare(projectId));
    }

    /** 成本预警中心 */
    @GetMapping("/warning-center")
    public Result<Map<String, Object>> warningCenter(@RequestParam Long projectId) {
        return Result.ok(svc.warningCenter(projectId));
    }
}
