package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcDirect;
import com.reip.proc.service.ProcDirectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 直接委托
 */
@RestController
@RequestMapping("/api/proc/directs")
@RequiredArgsConstructor
public class ProcDirectController {

    private final ProcDirectService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcDirect>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, status));
    }

    @GetMapping("/{id}")
    public Result<ProcDirect> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 阳光度：直接委托金额占全部定标金额比例 */
    @GetMapping("/sunlight")
    public Result<Map<String, Object>> sunlight(@RequestParam Long projectId) {
        return Result.ok(svc.sunlight(projectId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcDirect direct) {
        return Result.ok(svc.create(direct));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcDirect direct) {
        direct.setId(id);
        svc.update(direct);
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
}
