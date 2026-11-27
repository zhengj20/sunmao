package com.reip.fund.controller;

import com.reip.common.result.Result;
import com.reip.fund.service.FundPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 资金计划 /api/fund/plan
 */
@RestController
@RequestMapping("/api/fund/plan")
@RequiredArgsConstructor
public class FundPlanController {

    private final FundPlanService svc;

    /** 按月查询：各项目收付计划 + 汇总 + 大额付款标记 */
    @GetMapping
    public Result<Map<String, Object>> monthPlan(@RequestParam String month) {
        return Result.ok(svc.monthPlan(month));
    }

    /** 自动汇总生成（幂等：覆盖 DRAFT，不动 FINAL） */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestParam String month) {
        return Result.ok(svc.generate(month));
    }

    /** 计划定稿 */
    @PutMapping("/{id}/finalize")
    public Result<Void> finalizePlan(@PathVariable Long id) {
        svc.finalizePlan(id);
        return Result.ok();
    }
}
