package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleRefundExchange;
import com.reip.sales.service.SaleRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 退换管理：登记 / 提交审批（退定 SALE_SUBSCRIPTION_TERMINATE / 退房 SALE_REFUND）
 */
@RestController
@RequestMapping("/api/sales/refunds")
@RequiredArgsConstructor
public class SaleRefundController {

    private final SaleRefundService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleRefundExchange>> page(@RequestParam(defaultValue = "1") long current,
                                                         @RequestParam(defaultValue = "20") long size,
                                                         @RequestParam(required = false) Long projectId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String type) {
        return Result.ok(svc.page(current, size, projectId, status, type));
    }

    @GetMapping("/{id}")
    public Result<SaleRefundExchange> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 登记退换单（DRAFT）：body 传 contractId(退房) 或 subscriptionId(退定) + reason */
    @PostMapping
    public Result<Long> create(@RequestBody SaleRefundExchange re) {
        return Result.ok(svc.create(re));
    }

    /** 提交审批（Flowable 发起） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }
}
