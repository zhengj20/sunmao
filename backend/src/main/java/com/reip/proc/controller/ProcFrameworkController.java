package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcFramework;
import com.reip.proc.entity.ProcFrameworkOrder;
import com.reip.proc.service.ProcFrameworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 战略集采
 */
@RestController
@RequestMapping("/api/proc/frameworks")
@RequiredArgsConstructor
public class ProcFrameworkController {

    private final ProcFrameworkService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcFramework>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String kw,
                                                 @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, kw, status));
    }

    @GetMapping("/{id}")
    public Result<ProcFramework> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcFramework framework) {
        return Result.ok(svc.create(framework));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcFramework framework) {
        framework.setId(id);
        svc.update(framework);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 集采下单 */
    @PostMapping("/{id}/orders")
    public Result<Long> order(@PathVariable Long id, @RequestBody ProcFrameworkOrder order) {
        return Result.ok(svc.order(id, order));
    }

    /** 执行量统计 */
    @GetMapping("/{id}/usage")
    public Result<Map<String, Object>> usage(@PathVariable Long id) {
        return Result.ok(svc.usage(id));
    }
}
