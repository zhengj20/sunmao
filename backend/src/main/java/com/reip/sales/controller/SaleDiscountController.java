package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleDiscount;
import com.reip.sales.service.SalePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 折扣方案（分级权限）维护
 */
@RestController
@RequestMapping("/api/sales/discounts")
@RequiredArgsConstructor
public class SaleDiscountController {

    private final SalePriceService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleDiscount>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String level,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(svc.pageDiscounts(current, size, level, status));
    }

    @GetMapping("/{id}")
    public Result<SaleDiscount> detail(@PathVariable Long id) {
        return Result.ok(svc.discountDetail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleDiscount d) {
        return Result.ok(svc.createDiscount(d));
    }

    @PutMapping
    public Result<Void> update(@RequestBody SaleDiscount d) {
        svc.updateDiscount(d);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.deleteDiscount(id);
        return Result.ok();
    }
}
