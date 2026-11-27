package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpContract;
import com.reip.expense.entity.ExpPayment;
import com.reip.expense.service.ExpContractService;
import com.reip.expense.service.ExpPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用合同：台账 + 付款跟踪 + 合同付款
 */
@RestController
@RequestMapping("/api/expense/contracts")
@RequiredArgsConstructor
public class ExpContractController {

    private final ExpContractService svc;
    private final ExpPaymentService paySvc;

    @GetMapping("/page")
    public Result<PageResult<ExpContract>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String supplier,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, status, supplier, kw));
    }

    /** 付款单关联下拉（生效中合同） */
    @GetMapping("/options")
    public Result<List<ExpContract>> options() {
        return Result.ok(svc.options());
    }

    @GetMapping("/{id}")
    public Result<ExpContract> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpContract form) {
        return Result.ok(svc.create(form));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpContract form) {
        svc.update(form);
        return Result.ok();
    }

    /** 状态维护：ACTIVE/EXPIRED/TERMINATED */
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable String status) {
        svc.changeStatus(id, status);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 合同付款记录 */
    @GetMapping("/{id}/payments")
    public Result<List<ExpPayment>> payments(@PathVariable Long id) {
        return Result.ok(paySvc.listByContract(id));
    }

    /** 为合同新建付款单（DRAFT，不关联报销单）；实付走 /api/expense/payments/{id}/pay */
    @PostMapping("/{id}/payments")
    public Result<Long> createPayment(@PathVariable Long id, @RequestBody(required = false) AmountForm form) {
        return Result.ok(paySvc.createForContract(id, form == null ? null : form.getAmount()));
    }

    @Data
    public static class AmountForm {
        private BigDecimal amount;
    }
}
