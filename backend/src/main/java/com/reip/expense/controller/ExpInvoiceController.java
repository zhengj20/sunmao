package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpInvoice;
import com.reip.expense.service.ExpInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 发票池（全集团进项）：R2 查重 / 验真 / OCR(占位)
 */
@RestController
@RequestMapping("/api/expense/invoices")
@RequiredArgsConstructor
public class ExpInvoiceController {

    private final ExpInvoiceService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpInvoice>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String supplier,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(required = false) String verifyStatus,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, supplier, source, verifyStatus, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpInvoice> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpInvoice i) {
        return Result.ok(svc.create(i));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpInvoice i) {
        svc.update(i);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 验真（占位实现，待接 ai-service） */
    @PostMapping("/{id}/verify")
    public Result<ExpInvoice> verify(@PathVariable Long id) {
        return Result.ok(svc.verifyInvoice(id));
    }

    /** OCR 识别（占位实现，待接 ai-service） */
    @PostMapping("/ocr")
    public Result<Map<String, Object>> ocr(@RequestBody(required = false) Map<String, Object> input) {
        return Result.ok(svc.ocrRecognize(input));
    }
}
