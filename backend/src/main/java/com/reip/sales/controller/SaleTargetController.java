package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleTarget;
import com.reip.sales.service.SaleTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 销售目标：CRUD + 周期实际完成值与达成率
 */
@RestController
@RequestMapping("/api/sales/targets")
@RequiredArgsConstructor
public class SaleTargetController {

    private final SaleTargetService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleTarget>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String periodType,
                                               @RequestParam(required = false) String metric) {
        return Result.ok(svc.page(current, size, projectId, periodType, metric));
    }

    @GetMapping("/{id}")
    public Result<SaleTarget> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleTarget target) {
        return Result.ok(svc.create(target));
    }

    @PutMapping
    public Result<Void> update(@RequestBody SaleTarget target) {
        svc.update(target);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
