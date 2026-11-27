package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpPayment;
import com.reip.expense.service.ExpPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销付款单：生成 / 实付登记(回写报销 PAID)
 */
@RestController
@RequestMapping("/api/expense/payments")
@RequiredArgsConstructor
public class ExpPaymentController {

    private final ExpPaymentService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpPayment>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long reimbId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, reimbId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpPayment> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 为审批通过的报销生成付款单（可选关联费用合同） */
    @PostMapping
    public Result<Long> create(@RequestBody CreateForm form) {
        return Result.ok(svc.create(form.getReimbId(), form.getAmount(), form.getContractId()));
    }

    /** 实付登记：付款单 PAID + 报销单回写 PAID */
    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id, @RequestBody(required = false) PayForm form) {
        svc.pay(id, form == null ? null : form.getAmount(), form == null ? null : form.getPaidDate());
        return Result.ok();
    }

    @Data
    public static class CreateForm {
        private Long reimbId;
        private Long contractId;
        private BigDecimal amount;
    }

    @Data
    public static class PayForm {
        private BigDecimal amount;
        private LocalDate paidDate;
    }
}
