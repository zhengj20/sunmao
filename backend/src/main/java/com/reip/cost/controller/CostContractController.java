package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostContractAlloc;
import com.reip.cost.entity.CostPaymentPlan;
import com.reip.cost.service.CostContractService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 合同台账：评审/分摊科目/付款计划/结算/变更率
 */
@RestController
@RequestMapping("/api/cost/contracts")
@RequiredArgsConstructor
public class CostContractController {

    private final CostContractService svc;

    @GetMapping("/page")
    public Result<PageResult<CostContract>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long supplierId,
                                                 @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, supplierId, kw));
    }

    @GetMapping("/{id}")
    public Result<CostContract> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostContract contract) {
        return Result.ok(svc.create(contract));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostContract contract) {
        svc.update(contract);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 提交评审 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 结算：金额=签约+确认变更-扣款（强校验） */
    @Data
    public static class SettleForm {
        private BigDecimal settledAmount;
    }

    @PutMapping("/{id}/settle")
    public Result<Void> settle(@PathVariable Long id, @RequestBody SettleForm form) {
        svc.settle(id, form.getSettledAmount());
        return Result.ok();
    }

    /** 质保金释放 */
    @Data
    public static class ReleaseRetentionForm {
        private BigDecimal releaseAmount;
    }

    @PutMapping("/{id}/release-retention")
    public Result<Void> releaseRetention(@PathVariable Long id, @RequestBody ReleaseRetentionForm form) {
        svc.releaseRetention(id, form.getReleaseAmount());
        return Result.ok();
    }

    /** 变更率预警 */
    @GetMapping("/{id}/change-rate")
    public Result<Map<String, Object>> changeRate(@PathVariable Long id) {
        return Result.ok(svc.changeRate(id));
    }

    // ==================== 金额分摊科目 ====================

    @GetMapping("/{id}/allocs")
    public Result<List<CostContractAlloc>> allocs(@PathVariable Long id) {
        return Result.ok(svc.allocs(id));
    }

    @PostMapping("/{id}/allocs")
    public Result<Long> addAlloc(@PathVariable Long id, @RequestBody CostContractAlloc alloc) {
        return Result.ok(svc.addAlloc(id, alloc));
    }

    @PutMapping("/{id}/allocs")
    public Result<Void> updateAlloc(@PathVariable Long id, @RequestBody CostContractAlloc alloc) {
        svc.updateAlloc(alloc);
        return Result.ok();
    }

    @DeleteMapping("/{id}/allocs/{allocId}")
    public Result<Void> deleteAlloc(@PathVariable Long id, @PathVariable Long allocId) {
        svc.deleteAlloc(id, allocId);
        return Result.ok();
    }

    // ==================== 付款计划 ====================

    @GetMapping("/{id}/payment-plans")
    public Result<List<CostPaymentPlan>> paymentPlans(@PathVariable Long id) {
        return Result.ok(svc.paymentPlans(id));
    }

    @PostMapping("/{id}/payment-plans")
    public Result<Long> addPaymentPlan(@PathVariable Long id, @RequestBody CostPaymentPlan plan) {
        return Result.ok(svc.addPaymentPlan(id, plan));
    }

    @PutMapping("/{id}/payment-plans")
    public Result<Void> updatePaymentPlan(@PathVariable Long id, @RequestBody CostPaymentPlan plan) {
        svc.updatePaymentPlan(plan);
        return Result.ok();
    }

    @DeleteMapping("/{id}/payment-plans/{planId}")
    public Result<Void> deletePaymentPlan(@PathVariable Long id, @PathVariable Long planId) {
        svc.deletePaymentPlan(id, planId);
        return Result.ok();
    }
}
