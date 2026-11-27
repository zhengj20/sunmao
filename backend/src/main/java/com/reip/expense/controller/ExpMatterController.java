package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpMatter;
import com.reip.expense.service.ExpMatterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 事项申请（通用事项审批单）
 */
@RestController
@RequestMapping("/api/expense/matters")
@RequiredArgsConstructor
public class ExpMatterController {

    private final ExpMatterService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpMatter>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) Long deptId,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, deptId, type, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpMatter> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpMatter form) {
        return Result.ok(svc.create(form));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpMatter form) {
        svc.update(form);
        return Result.ok();
    }

    /** 提交审批（EXP_MATTER：部门→财务） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
