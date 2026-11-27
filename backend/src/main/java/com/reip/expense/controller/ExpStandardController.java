package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpStandard;
import com.reip.expense.service.ExpStandardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 费控标准库（职级×城市×类型）
 */
@RestController
@RequestMapping("/api/expense/standards")
@RequiredArgsConstructor
public class ExpStandardController {

    private final ExpStandardService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpStandard>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String level,
                                                @RequestParam(required = false) String cityLevel,
                                                @RequestParam(required = false) String type) {
        return Result.ok(svc.page(current, size, level, cityLevel, type));
    }

    @GetMapping("/{id}")
    public Result<ExpStandard> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpStandard s) {
        return Result.ok(svc.create(s));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExpStandard s) {
        svc.update(s);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
