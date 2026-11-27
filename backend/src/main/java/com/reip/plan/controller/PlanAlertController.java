package com.reip.plan.controller;

import com.reip.common.result.Result;
import com.reip.plan.service.PlanAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 计划预警：红黄绿灯重算 / 滞后连锁影响分析 / 红灯督办推送R3
 */
@RestController
@RequestMapping("/api/plan/alerts")
@RequiredArgsConstructor
public class PlanAlertController {

    private final PlanAlertService svc;

    /** 重算项目全部任务预警颜色，返回变更条数 */
    @PostMapping("/recompute")
    public Result<Integer> recompute(@RequestParam Long projectId) {
        return Result.ok(svc.recomputeAll(projectId));
    }

    /** 滞后任务清单（YELLOW/RED） */
    @GetMapping("/delayed")
    public Result<List<Map<String, Object>>> delayed(@RequestParam Long projectId) {
        return Result.ok(svc.delayed(projectId));
    }

    /** 滞后连锁影响分析（下游受影响节点） */
    @GetMapping("/{taskId}/impact")
    public Result<Map<String, Object>> impact(@PathVariable Long taskId) {
        return Result.ok(svc.chainImpact(taskId));
    }

    /** R3 红灯推送集团运营 + 滞后专项督办清单 */
    @GetMapping("/supervision")
    public Result<Map<String, Object>> supervision(@RequestParam Long projectId) {
        return Result.ok(svc.supervision(projectId));
    }
}
