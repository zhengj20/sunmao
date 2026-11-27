package com.reip.sales.controller;

import com.reip.common.result.Result;
import com.reip.sales.service.SaleReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 销售报表：日报(三口径) / 去化率 / 价格实现率
 */
@RestController
@RequestMapping("/api/sales/reports")
@RequiredArgsConstructor
public class SaleReportController {

    private final SaleReportService svc;

    /** 销售日报：认购/签约/回款三口径 + 回款率 */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily(@RequestParam Long projectId) {
        return Result.ok(svc.daily(projectId));
    }

    /** 去化率：已售/总货值 */
    @GetMapping("/turnover")
    public Result<Map<String, Object>> turnover(@RequestParam Long projectId) {
        return Result.ok(svc.turnover(projectId));
    }

    /** 价格实现率：成交均价/表价均价 */
    @GetMapping("/price-achievement")
    public Result<Map<String, Object>> priceAchievement(@RequestParam Long projectId) {
        return Result.ok(svc.priceAchievement(projectId));
    }
}
