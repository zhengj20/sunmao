package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcAward;
import com.reip.proc.service.ProcAwardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 定标
 */
@RestController
@RequestMapping("/api/proc/awards")
@RequiredArgsConstructor
public class ProcAwardController {

    private final ProcAwardService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcAward>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) Long procProjectId,
                                              @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, procProjectId, status));
    }

    @GetMapping("/{id}")
    public Result<ProcAward> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcAward award) {
        return Result.ok(svc.create(award));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }
}
