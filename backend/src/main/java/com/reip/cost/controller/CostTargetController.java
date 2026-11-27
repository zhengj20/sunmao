package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostTargetItem;
import com.reip.cost.entity.CostTargetVersion;
import com.reip.cost.service.CostTargetService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 目标成本：版本/明细/审批/锁定/版本对比
 */
@RestController
@RequestMapping("/api/cost/targets")
@RequiredArgsConstructor
public class CostTargetController {

    private final CostTargetService svc;

    @GetMapping("/page")
    public Result<PageResult<CostTargetVersion>> page(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String stage,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, stage, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostTargetVersion> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @Data
    public static class TargetForm {
        private CostTargetVersion version;
        private List<CostTargetItem> items;
    }

    @PostMapping
    public Result<Long> create(@RequestBody TargetForm form) {
        return Result.ok(svc.create(form.getVersion(), form.getItems()));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostTargetVersion version) {
        svc.update(version);
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

    @PutMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id) {
        svc.lock(id);
        return Result.ok();
    }

    // ==================== 明细 ====================

    @GetMapping("/{id}/items")
    public Result<List<CostTargetItem>> items(@PathVariable Long id) {
        return Result.ok(svc.items(id));
    }

    @PostMapping("/{id}/items")
    public Result<Long> addItem(@PathVariable Long id, @RequestBody CostTargetItem item) {
        return Result.ok(svc.addItem(id, item));
    }

    @PutMapping("/{id}/items")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody CostTargetItem item) {
        svc.updateItem(id, item);
        return Result.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        svc.deleteItem(id, itemId);
        return Result.ok();
    }

    /** 版本对比：按科目聚合金额差与差异率（diff=B-A） */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long versionA, @RequestParam Long versionB) {
        return Result.ok(svc.compare(versionA, versionB));
    }

    /** 导出目标成本明细为 Excel 数据 */
    @GetMapping("/{id}/export")
    public Result<List<Map<String, Object>>> exportExcel(@PathVariable Long id) {
        return Result.ok(svc.exportExcel(id));
    }

    /** 导入 Excel 数据到目标成本明细 */
    @PostMapping("/{id}/import")
    public Result<Integer> importExcel(@PathVariable Long id, @RequestBody List<Map<String, String>> rows) {
        return Result.ok(svc.importExcel(id, rows));
    }
}
