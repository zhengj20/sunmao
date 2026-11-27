package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcPlan;
import com.reip.proc.entity.ProcPlanItem;
import com.reip.proc.service.ProcPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 采购计划（月度汇总）
 */
@RestController
@RequestMapping("/api/proc/plans")
@RequiredArgsConstructor
public class ProcPlanController {

    private final ProcPlanService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcPlan>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, status));
    }

    @GetMapping("/{id}")
    public Result<ProcPlan> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<ProcPlanItem>> items(@PathVariable Long id) {
        return Result.ok(svc.items(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcPlan plan) {
        return Result.ok(svc.create(plan));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcPlan plan) {
        plan.setId(id);
        svc.update(plan);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 把已审批需求并入计划（转为计划明细） */
    @PostMapping("/{id}/merge-requirements")
    public Result<Integer> mergeRequirements(@PathVariable Long id,
                                             @RequestBody Map<String, List<Long>> body) {
        return Result.ok(svc.mergeRequirements(id, body.get("requirementIds")));
    }

    /** 计划确认：DRAFT→CONFIRMED */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        svc.confirm(id);
        return Result.ok();
    }
}
