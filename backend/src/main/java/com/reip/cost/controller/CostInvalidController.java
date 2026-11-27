package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostInvalid;
import com.reip.cost.service.CostInvalidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 无效成本认定
 */
@RestController
@RequestMapping("/api/cost/invalids")
@RequiredArgsConstructor
public class CostInvalidController {

    private final CostInvalidService svc;

    @GetMapping("/page")
    public Result<PageResult<CostInvalid>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostInvalid> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostInvalid invalid) {
        return Result.ok(svc.create(invalid));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostInvalid invalid) {
        svc.update(invalid);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    /** 无效成本占比 = 已确认合计/动态成本合计 */
    @GetMapping("/rate")
    public Result<Map<String, Object>> rate(@RequestParam Long projectId) {
        return Result.ok(svc.rate(projectId));
    }
}
