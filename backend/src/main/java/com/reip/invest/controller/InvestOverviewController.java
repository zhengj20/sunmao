package com.reip.invest.controller;

import com.reip.common.result.Result;
import com.reip.invest.service.InvestOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 投资总览 / 投资驾驶舱 /api/invest
 */
@RestController
@RequestMapping("/api/invest")
@RequiredArgsConstructor
public class InvestOverviewController {

    private final InvestOverviewService svc;

    /** 投资总览：KPI + 项目投资清单 + 状态 / 城市分布 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(svc.overview());
    }

    /** 投资驾驶舱：KPI 卡 + 基线 vs 动态对比 + IRR 排名 + 趋势 + 业态构成 + 预警 */
    @GetMapping("/cockpit")
    public Result<Map<String, Object>> cockpit() {
        return Result.ok(svc.cockpit());
    }
}
