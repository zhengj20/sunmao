package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostPaymentApply;
import com.reip.cost.service.CostPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款申请：提交硬校验/实付登记
 */
@RestController
@RequestMapping("/api/cost/payments")
@RequiredArgsConstructor
public class CostPaymentController {

    private final CostPaymentService svc;

    @GetMapping("/page")
    public Result<PageResult<CostPaymentApply>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Long contractId,
                                                     @RequestParam(required = false) String paymentType,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, contractId, paymentType, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostPaymentApply> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostPaymentApply payment) {
        return Result.ok(svc.create(payment));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostPaymentApply payment) {
        svc.update(payment);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 提交审批（累计/发票/预付比例硬校验） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 实付登记 */
    @Data
    public static class MarkPaidForm {
        private BigDecimal paidAmount;
        private LocalDate paidDate;
    }

    @PutMapping("/{id}/mark-paid")
    public Result<Void> markPaid(@PathVariable Long id, @RequestBody MarkPaidForm form) {
        svc.markPaid(id, form.getPaidAmount(), form.getPaidDate());
        return Result.ok();
    }
}
