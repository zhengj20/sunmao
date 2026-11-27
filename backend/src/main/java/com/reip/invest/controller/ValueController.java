package com.reip.invest.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.invest.entity.InvValue;
import com.reip.invest.service.InvestValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 货值台账 /api/invest/value
 */
@RestController
@RequestMapping("/api/invest/value")
@RequiredArgsConstructor
public class ValueController {

    private final InvestValueService svc;

    @GetMapping("/page")
    public Result<PageResult<InvValue>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String productCategory) {
        return Result.ok(svc.page(current, size, projectId, productCategory));
    }

    @GetMapping("/{id}")
    public Result<InvValue> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新增（货值=面积×单价自动计算） */
    @PostMapping
    public Result<Long> create(@RequestBody InvValue value) {
        return Result.ok(svc.create(value));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody InvValue value) {
        value.setId(id);
        svc.update(value);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 供销存：期初存货+新供货−已售+退房 */
    @GetMapping("/stock")
    public Result<Map<String, Object>> stock(@RequestParam Long projectId) {
        return Result.ok(svc.stock(projectId));
    }
}
