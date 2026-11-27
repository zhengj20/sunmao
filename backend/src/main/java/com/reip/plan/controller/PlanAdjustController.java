package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.PlanAdjust;
import com.reip.plan.service.PlanAdjustService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 计划调整：登记(R1里程碑须填归类) / 提交审批(PLAN_ADJUST)
 */
@RestController
@RequestMapping("/api/plan/adjusts")
@RequiredArgsConstructor
public class PlanAdjustController {

    private final PlanAdjustService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanAdjust>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<PlanAdjust> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody PlanAdjust a) {
        return Result.ok(svc.create(a));
    }

    /** 提交审批（PLAN_ADJUST：项目运营→集团运营） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }
}
