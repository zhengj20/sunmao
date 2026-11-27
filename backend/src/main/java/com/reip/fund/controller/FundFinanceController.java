package com.reip.fund.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.fund.entity.FundFinance;
import com.reip.fund.service.FundFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 融资台账 /api/fund/finance
 */
@RestController
@RequestMapping("/api/fund/finance")
@RequiredArgsConstructor
public class FundFinanceController {

    private final FundFinanceService svc;

    @GetMapping("/page")
    public Result<PageResult<FundFinance>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(required = false) String financeType,
                                                 @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, financeType, status));
    }

    @GetMapping("/{id}")
    public Result<FundFinance> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody FundFinance finance) {
        return Result.ok(svc.create(finance));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FundFinance finance) {
        finance.setId(id);
        svc.update(finance);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 到期提醒（默认90天内） */
    @GetMapping("/expiring")
    public Result<List<FundFinance>> expiring(@RequestParam(defaultValue = "90") int days) {
        return Result.ok(svc.expiring(days));
    }
}
