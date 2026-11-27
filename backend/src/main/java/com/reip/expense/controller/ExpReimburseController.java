package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpReimburse;
import com.reip.expense.service.ExpReimburseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 报销：登记(带明细) / R2发票查重·验真 / R3超标特批提交 / 审批
 */
@RestController
@RequestMapping("/api/expense/reimburses")
@RequiredArgsConstructor
public class ExpReimburseController {

    private final ExpReimburseService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpReimburse>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long deptId,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, deptId, userId, type, status, kw));
    }

    @GetMapping("/{id}")
    public Result<ExpReimburse> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 登记报销（带明细行）；R3 超标须说明；关联发票须验真通过 */
    @PostMapping
    public Result<Long> create(@RequestBody ExpReimburse r) {
        return Result.ok(svc.create(r));
    }

    /** 提交：超标走 EXP_REIMB_OVERRUN 特批；未超标直接生效并核销 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        svc.submit(id);
        return Result.ok();
    }
}
