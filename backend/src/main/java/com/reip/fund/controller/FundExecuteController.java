package com.reip.fund.controller;

import com.reip.common.result.Result;
import com.reip.fund.service.FundPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 收付执行 /api/fund/execute
 */
@RestController
@RequestMapping("/api/fund/execute")
@RequiredArgsConstructor
public class FundExecuteController {

    private final FundPlanService svc;

    /** 计划 vs 实际对比（含执行率） */
    @GetMapping
    public Result<Map<String, Object>> execute(@RequestParam String month) {
        return Result.ok(svc.execute(month));
    }

    /** 资金日报：当日实付清单（大额标记）+ 汇总 */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily(@RequestParam String date) {
        return Result.ok(svc.daily(date));
    }
}
