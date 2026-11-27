package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpLoan;
import com.reip.expense.service.ExpLoanService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 借款：登记+审批发起 / R4 额度控制 / 冲销
 */
@RestController
@RequestMapping("/api/expense/loans")
@RequiredArgsConstructor
public class ExpLoanController {

    private final ExpLoanService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpLoan>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, userId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpLoan> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 登记并发起借款审批（R4 未冲销/逾期/额度阻断） */
    @PostMapping
    public Result<Long> create(@RequestBody ExpLoan l) {
        return Result.ok(svc.create(l));
    }

    /** 冲销/还款：未冲销余额扣减，清零置 WRITEOFF */
    @PostMapping("/{id}/writeoff")
    public Result<Void> writeoff(@PathVariable Long id, @RequestBody(required = false) WriteoffForm form) {
        svc.writeoff(id, form == null ? null : form.getAmount());
        return Result.ok();
    }

    @Data
    public static class WriteoffForm {
        private BigDecimal amount;
    }
}
