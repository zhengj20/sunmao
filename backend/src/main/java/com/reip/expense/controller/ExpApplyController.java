package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpApply;
import com.reip.expense.service.ExpApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 费用事前申请：登记 / R1 提交(占用预算) / 审批
 */
@RestController
@RequestMapping("/api/expense/applies")
@RequiredArgsConstructor
public class ExpApplyController {

    private final ExpApplyService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpApply>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long deptId,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, deptId, type, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpApply> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpApply a) {
        return Result.ok(svc.create(a));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpApply a) {
        svc.update(a);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 提交审批：R1 预算占用硬校验 + 发起 EXP_BUDGET 审批 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }
}
