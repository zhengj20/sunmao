package com.reip.invest.controller;

import com.reip.common.result.Result;
import com.reip.invest.entity.InvDynamicProfit;
import com.reip.invest.service.InvestProfitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 动态利润 /api/invest/profit
 */
@RestController
@RequestMapping("/api/invest/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final InvestProfitService svc;

    /** 刷新动态利润（同月幂等；也由 cost.dynamic.updated 事件自动触发） */
    @PostMapping("/refresh")
    public Result<InvDynamicProfit> refresh(@RequestParam Long projectId,
                                            @RequestParam(required = false) String month) {
        return Result.ok(svc.refresh(projectId, month));
    }

    /** 最新动态利润 + 拿地基线对比 */
    @GetMapping
    public Result<Map<String, Object>> latest(@RequestParam Long projectId) {
        return Result.ok(svc.latest(projectId));
    }
}
