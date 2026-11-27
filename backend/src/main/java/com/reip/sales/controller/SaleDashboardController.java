package com.reip.sales.controller;

import com.reip.common.result.Result;
import com.reip.sales.service.SaleDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 销售驾驶舱 / 销售总览 / 置业顾问业绩 / 蓄客漏斗 / 渠道结构（只读聚合）
 */
@RestController
@RequestMapping("/api/sales/dashboard")
@RequiredArgsConstructor
public class SaleDashboardController {

    private final SaleDashboardService svc;

    /** 销售驾驶舱 */
    @GetMapping("/cockpit")
    public Result<Map<String, Object>> cockpit(@RequestParam Long projectId) {
        return Result.ok(svc.cockpit(projectId));
    }

    /** 销售总览（项目进度 + 楼栋销控 + 户型 + 均价走势） */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam Long projectId) {
        return Result.ok(svc.overview(projectId));
    }

    /** 置业顾问业绩 */
    @GetMapping("/advisor")
    public Result<Map<String, Object>> advisor(@RequestParam Long projectId) {
        return Result.ok(svc.advisor(projectId));
    }

    /** 蓄客漏斗 */
    @GetMapping("/funnel")
    public Result<Map<String, Object>> funnel(@RequestParam Long projectId) {
        return Result.ok(svc.funnel(projectId));
    }

    /** 渠道结构（渠道管理页复用） */
    @GetMapping("/channel")
    public Result<Map<String, Object>> channel(@RequestParam Long projectId) {
        return Result.ok(svc.channelStructure(projectId));
    }
}
