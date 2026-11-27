package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleSubscription;
import com.reip.sales.service.SaleSubscriptionService;
import com.reip.sales.service.SaleSubscriptionService.ConvertForm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认购管理：登记 / 提交审批 / 转签约 / 退定
 */
@RestController
@RequestMapping("/api/sales/subscriptions")
@RequiredArgsConstructor
public class SaleSubscriptionController {

    private final SaleSubscriptionService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleSubscription>> page(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<SaleSubscription> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleSubscription s) {
        return Result.ok(svc.create(s));
    }

    /** 提交审批（Flowable 发起 SALE_SUBSCRIPTION） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 转签约：生成合同草稿 */
    @PostMapping("/{id}/convert")
    public Result<Long> convert(@PathVariable Long id, @RequestBody(required = false) ConvertForm form) {
        return Result.ok(svc.convert(id, form));
    }

    /** 退定：生成退换单并提交审批 */
    @PostMapping("/{id}/refund")
    public Result<Long> refund(@PathVariable Long id, @RequestBody(required = false) RefundForm form) {
        return Result.ok(svc.refund(id, form == null ? null : form.getReason()));
    }

    /** 超期未签约预警标记 */
    @PostMapping("/warn-overdue")
    public Result<Integer> warnOverdue() {
        return Result.ok(svc.warnOverdue());
    }

    @Data
    public static class RefundForm {
        private String reason;
    }
}
