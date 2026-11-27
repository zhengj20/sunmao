package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcMisc;
import com.reip.proc.entity.ProcMiscCompare;
import com.reip.proc.service.ProcMiscService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 零星采购
 */
@RestController
@RequestMapping("/api/proc/misc")
@RequiredArgsConstructor
public class ProcMiscController {

    private final ProcMiscService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcMisc>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, status));
    }

    @GetMapping("/{id}")
    public Result<ProcMisc> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcMisc misc) {
        return Result.ok(svc.create(misc));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcMisc misc) {
        misc.setId(id);
        svc.update(misc);
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

    // ================= 比价子表 =================
    @GetMapping("/{id}/compares")
    public Result<List<ProcMiscCompare>> listCompares(@PathVariable Long id) {
        return Result.ok(svc.listCompares(id));
    }

    @PostMapping("/{id}/compares")
    public Result<Long> addCompare(@PathVariable Long id, @RequestBody ProcMiscCompare compare) {
        return Result.ok(svc.addCompare(id, compare));
    }

    @DeleteMapping("/{id}/compares/{compareId}")
    public Result<Void> deleteCompare(@PathVariable Long id, @PathVariable Long compareId) {
        svc.deleteCompare(id, compareId);
        return Result.ok();
    }
}
