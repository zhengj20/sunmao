package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleMortgage;
import com.reip.sales.entity.SalePaymentPlan;
import com.reip.sales.entity.SaleReceipt;
import com.reip.sales.service.SalePaymentService;
import com.reip.sales.service.SalePaymentService.ReceiveForm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回款管理：应收计划 / 实收核销 / 按揭跟踪 / 逾期清单
 */
@RestController
@RequestMapping("/api/sales/payments")
@RequiredArgsConstructor
public class SalePaymentController {

    private final SalePaymentService svc;

    // ==================== 应收计划 ====================

    @GetMapping("/plans/page")
    public Result<PageResult<SalePaymentPlan>> pagePlans(@RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "20") long size,
                                                          @RequestParam(required = false) Long contractId,
                                                          @RequestParam(required = false) String status) {
        return Result.ok(svc.pagePlans(current, size, contractId, status));
    }

    @GetMapping("/plans/{contractId}")
    public Result<List<SalePaymentPlan>> plans(@PathVariable Long contractId) {
        return Result.ok(svc.plansByContract(contractId));
    }

    /** 逾期清单（自动标记 OVERDUE） */
    @GetMapping("/overdue")
    public Result<List<SalePaymentPlan>> overdue(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.overdue(projectId));
    }

    // ==================== 实收 ====================

    @GetMapping("/receipts/page")
    public Result<PageResult<SaleReceipt>> pageReceipts(@RequestParam(defaultValue = "1") long current,
                                                         @RequestParam(defaultValue = "20") long size,
                                                         @RequestParam(required = false) Long contractId) {
        return Result.ok(svc.pageReceipts(current, size, contractId));
    }

    /** 实收登记（核销应收 R5） */
    @PostMapping("/receipts/{contractId}")
    public Result<Long> receive(@PathVariable Long contractId, @RequestBody ReceiveForm form) {
        return Result.ok(svc.receive(contractId, form));
    }

    // ==================== 按揭 ====================

    @GetMapping("/mortgages/page")
    public Result<PageResult<SaleMortgage>> pageMortgages(@RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "20") long size,
                                                           @RequestParam(required = false) Long contractId) {
        return Result.ok(svc.pageMortgages(current, size, contractId));
    }

    @PostMapping("/mortgages")
    public Result<Long> createMortgage(@RequestBody SaleMortgage m) {
        return Result.ok(svc.createMortgage(m));
    }

    @PutMapping("/mortgages")
    public Result<Void> updateMortgage(@RequestBody SaleMortgage m) {
        svc.updateMortgage(m);
        return Result.ok();
    }
}
