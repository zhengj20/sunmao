package com.reip.invest.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.invest.entity.InvCalcVersion;
import com.reip.invest.service.InvestCalcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 投资测算 /api/invest/calc
 */
@RestController
@RequestMapping("/api/invest/calc")
@RequiredArgsConstructor
public class CalcController {

    private final InvestCalcService svc;

    /** 版本分页 */
    @GetMapping("/page")
    public Result<PageResult<InvCalcVersion>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String stage,
                                                    @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, stage, status));
    }

    @GetMapping("/{id}")
    public Result<InvCalcVersion> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新建测算版本（version_code 自动生成，净利率自动计算） */
    @PostMapping
    public Result<Long> create(@RequestBody InvCalcVersion version) {
        return Result.ok(svc.create(version));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody InvCalcVersion version) {
        version.setId(id);
        svc.update(version);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 重新计算 IRR + 动态回收期（基于月度现金流）并落库 */
    @PostMapping("/{id}/recalc")
    public Result<Map<String, Object>> recalc(@PathVariable Long id) {
        return Result.ok(svc.recalc(id));
    }

    /** 锁定版本（作为后评估基准，锁定后禁改） */
    @PutMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id) {
        svc.lock(id);
        return Result.ok();
    }

    /** 后评估：拿地测算 vs 当前动态，量/价/成本三因素差异分解 */
    @GetMapping("/{id}/post-eval")
    public Result<Map<String, Object>> postEval(@PathVariable Long id) {
        return Result.ok(svc.postEval(id));
    }
}
