package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostIndicator;
import com.reip.cost.service.CostIndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成本指标库
 */
@RestController
@RequestMapping("/api/cost/indicators")
@RequiredArgsConstructor
public class CostIndicatorController {

    private final CostIndicatorService svc;

    @GetMapping("/page")
    public Result<PageResult<CostIndicator>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) String accountCode,
                                                  @RequestParam(required = false) String productCategory,
                                                  @RequestParam(required = false) String indicatorType,
                                                  @RequestParam(required = false) String source,
                                                  @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, accountCode, productCategory, indicatorType, source, kw));
    }

    @GetMapping("/{id}")
    public Result<CostIndicator> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostIndicator indicator) {
        return Result.ok(svc.create(indicator));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostIndicator indicator) {
        svc.update(indicator);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 匹配指标供测算引用 */
    @GetMapping("/suggest")
    public Result<List<CostIndicator>> suggest(@RequestParam(required = false) String accountCode,
                                               @RequestParam(required = false) String productCategory) {
        return Result.ok(svc.suggest(accountCode, productCategory));
    }
}
