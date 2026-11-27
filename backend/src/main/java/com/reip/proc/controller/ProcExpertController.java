package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcExpert;
import com.reip.proc.service.ProcExpertService;
import com.reip.proc.service.ProcExpertService.ExtractRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评标专家库
 */
@RestController
@RequestMapping("/api/proc/experts")
@RequiredArgsConstructor
public class ProcExpertController {

    private final ProcExpertService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcExpert>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String specialty,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, specialty, status));
    }

    @GetMapping
    public Result<List<ProcExpert>> list(@RequestParam(required = false) String specialty,
                                         @RequestParam(required = false) String status) {
        return Result.ok(svc.list(specialty, status));
    }

    @GetMapping("/{id}")
    public Result<ProcExpert> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcExpert expert) {
        return Result.ok(svc.create(expert));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcExpert expert) {
        expert.setId(id);
        svc.update(expert);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/extract")
    public Result<List<ProcExpert>> extract(@RequestBody ExtractRequest req) {
        return Result.ok(svc.extract(req));
    }
}
