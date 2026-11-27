package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.service.ProcProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 招采立项
 */
@RestController
@RequestMapping("/api/proc/projects")
@RequiredArgsConstructor
public class ProcProjectController {

    private final ProcProjectService svc;

    @GetMapping("/page")
    public Result<PageResult<ProcProject>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ProcProject> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 采购方式矩阵建议（阳光采购：≥门槛建议公开招标） */
    @GetMapping("/suggest-method")
    public Result<Map<String, Object>> suggestMethod(@RequestParam BigDecimal amount) {
        return Result.ok(svc.suggestMethod(amount));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcProject project) {
        return Result.ok(svc.create(project));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcProject project) {
        project.setId(id);
        svc.update(project);
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
