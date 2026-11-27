package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostInvoice;
import com.reip.cost.service.CostInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收票登记（发票池）
 */
@RestController
@RequestMapping("/api/cost/invoices")
@RequiredArgsConstructor
public class CostInvoiceController {

    private final CostInvoiceService svc;

    @GetMapping("/page")
    public Result<PageResult<CostInvoice>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) Long contractId,
                                                @RequestParam(required = false) String checkStatus,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, contractId, checkStatus, kw));
    }

    @GetMapping("/{id}")
    public Result<CostInvoice> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostInvoice invoice) {
        return Result.ok(svc.create(invoice));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostInvoice invoice) {
        svc.update(invoice);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
