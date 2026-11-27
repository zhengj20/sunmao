package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.service.SaleContractService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 签约管理：登记 / R3折扣刚性提交 / 特价审批 / 退房
 */
@RestController
@RequestMapping("/api/sales/contracts")
@RequiredArgsConstructor
public class SaleContractController {

    private final SaleContractService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleContract>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<SaleContract> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleContract c) {
        return Result.ok(svc.create(c));
    }

    /** 提交审批（R3 折扣刚性校验 + Flowable 发起 SALE_CONTRACT） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 发起特价房审批（超权限折扣前置） */
    @PostMapping("/{id}/special-price")
    public Result<Void> specialPrice(@PathVariable Long id) {
        svc.submitSpecialPrice(id);
        return Result.ok();
    }

    /** 退房：生成退换单并提交 SALE_REFUND 审批 */
    @PostMapping("/{id}/refund")
    public Result<Long> refund(@PathVariable Long id, @RequestBody(required = false) RefundForm form) {
        return Result.ok(svc.refund(id, form == null ? null : form.getReason()));
    }

    @Data
    public static class RefundForm {
        private String reason;
    }
}
