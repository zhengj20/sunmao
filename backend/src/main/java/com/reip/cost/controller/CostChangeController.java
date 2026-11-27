package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostChange;
import com.reip.cost.service.CostChangeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 变更管理：两阶段提交（估价→确认）
 */
@RestController
@RequestMapping("/api/cost/changes")
@RequiredArgsConstructor
public class CostChangeController {

    private final CostChangeService svc;

    @GetMapping("/page")
    public Result<PageResult<CostChange>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) Long contractId,
                                               @RequestParam(required = false) String changeType,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, contractId, changeType, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostChange> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostChange change) {
        return Result.ok(svc.create(change));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostChange change) {
        svc.update(change);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 一阶段：估价提交 */
    @PostMapping("/{id}/submit-estimate")
    public Result<Void> submitEstimate(@PathVariable Long id) {
        svc.submitEstimate(id);
        return Result.ok();
    }

    /** 二阶段：确认提交 */
    @Data
    public static class ConfirmForm {
        private BigDecimal confirmedAmount;
        private BigDecimal deductAmount;
    }

    @PostMapping("/{id}/submit-confirm")
    public Result<Void> submitConfirm(@PathVariable Long id, @RequestBody(required = false) ConfirmForm form) {
        svc.submitConfirm(id, form != null ? form.getConfirmedAmount() : null, form != null ? form.getDeductAmount() : null);
        return Result.ok();
    }
}
