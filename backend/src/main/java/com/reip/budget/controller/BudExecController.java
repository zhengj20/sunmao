package com.reip.budget.controller;

import com.reip.budget.service.BudExecService;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 预算执行分析：年度总览 / 月度趋势 / 管控链（年度→月度→资金计划）
 */
@RestController
@RequestMapping("/api/budget/exec")
@RequiredArgsConstructor
public class BudExecController {

    private final BudExecService svc;

    /** 年度执行总览：成本线/费用线 预算-分解-执行-执行率 + 科目明细 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return Result.ok(svc.overview(y));
    }

    /** 月度趋势：12 个月 预算 vs 执行（成本线/费用线） */
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return Result.ok(svc.trend(y));
    }

    /** 月度资金计划执行：12 个月 资金计划（成本=合同付款计划、费用=当月费用预算）vs 实际（成本付款/费用占用+核销） */
    @GetMapping("/fund-months")
    public Result<Map<String, Object>> fundMonths(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return Result.ok(svc.fundMonths(y));
    }

    /** 管控链（B5）：年度预算 → 月度预算 → 资金计划（成本线=合同付款计划、费用线=当月费用预算） */
    @GetMapping("/chain")
    public Result<Map<String, Object>> chain(@RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return Result.ok(svc.chain(year == null ? now.getYear() : year,
                month == null ? now.getMonthValue() : month));
    }
}
