package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpBudget;
import com.reip.expense.service.ExpBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 费用预算：编制 / 四栏台账
 */
@RestController
@RequestMapping("/api/expense/budgets")
@RequiredArgsConstructor
public class ExpBudgetController {

    private final ExpBudgetService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpBudget>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) Long deptId,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) String accountName,
                                              @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, deptId, year, accountName, status));
    }

    @GetMapping("/ledger")
    public Result<List<ExpBudget>> ledger(@RequestParam(required = false) Long deptId,
                                          @RequestParam(required = false) Integer year) {
        return Result.ok(svc.ledger(deptId, year));
    }

    @GetMapping("/{id}")
    public Result<ExpBudget> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpBudget b) {
        return Result.ok(svc.create(b));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpBudget b) {
        svc.update(b);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
