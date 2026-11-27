package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostPlan;
import com.reip.cost.entity.CostPlanItem;
import com.reip.cost.service.CostPlanService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合约规划：规划/明细/余量/审批/三联穿透
 */
@RestController
@RequestMapping("/api/cost/plans")
@RequiredArgsConstructor
public class CostPlanController {

    private final CostPlanService svc;

    @GetMapping("/page")
    public Result<PageResult<CostPlan>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostPlan> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @Data
    public static class PlanForm {
        private CostPlan plan;
        private List<CostPlanItem> items;
    }

    @PostMapping
    public Result<Long> create(@RequestBody PlanForm form) {
        return Result.ok(svc.create(form.getPlan(), form.getItems()));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostPlan plan) {
        svc.update(plan);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 提交规划调整审批 */
    @PostMapping("/{id}/submit-adjustment")
    public Result<Void> submitAdjustment(@PathVariable Long id) {
        svc.submitAdjustment(id);
        return Result.ok();
    }

    // ==================== 明细 ====================

    @GetMapping("/{id}/items")
    public Result<List<CostPlanItem>> items(@PathVariable Long id) {
        return Result.ok(svc.items(id));
    }

    @PostMapping("/{id}/items")
    public Result<Long> addItem(@PathVariable Long id, @RequestBody CostPlanItem item) {
        return Result.ok(svc.addItem(id, item));
    }

    @PutMapping("/{id}/items")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody CostPlanItem item) {
        svc.updateItem(id, item);
        return Result.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        svc.deleteItem(id, itemId);
        return Result.ok();
    }

    /** 三联穿透：规划明细下挂的合同列表 */
    @GetMapping("/{id}/items/{itemId}/drill")
    public Result<List<CostContract>> drill(@PathVariable Long id, @PathVariable Long itemId) {
        return Result.ok(svc.drill(id, itemId));
    }
}
