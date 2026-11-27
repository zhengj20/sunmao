package com.reip.expense.controller;

import com.reip.common.result.Result;
import com.reip.expense.service.ExpReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 费用分析：预算执行 / 三费占比·费效比 / 异常识别
 */
@RestController
@RequestMapping("/api/expense/reports")
@RequiredArgsConstructor
public class ExpReportController {

    private final ExpReportService svc;

    @GetMapping("/exec")
    public Result<Map<String, Object>> exec() {
        return Result.ok(svc.exec());
    }

    @GetMapping("/three-fee")
    public Result<Map<String, Object>> threeFee() {
        return Result.ok(svc.threeFee());
    }

    @GetMapping("/anomaly")
    public Result<Map<String, Object>> anomaly() {
        return Result.ok(svc.anomaly());
    }

    /** 费用总览：KPI + 待办计数 + 趋势 + 部门 Top */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(svc.overview());
    }
}
