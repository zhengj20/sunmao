package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcRequirement;
import com.reip.proc.service.ProcRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 采购需求
 */
@RestController
@RequestMapping("/api/proc/requirements")
@RequiredArgsConstructor
public class ProcRequirementController {

    private final ProcRequirementService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcRequirement>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ProcRequirement> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcRequirement req) {
        return Result.ok(svc.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcRequirement req) {
        req.setId(id);
        svc.update(req);
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
