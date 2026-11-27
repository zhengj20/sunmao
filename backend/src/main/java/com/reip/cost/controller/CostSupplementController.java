package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostSupplement;
import com.reip.cost.service.CostSupplementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 合同补充协议：CRUD/审批
 */
@RestController
@RequestMapping("/api/cost/supplements")
@RequiredArgsConstructor
public class CostSupplementController {

    private final CostSupplementService svc;

    @GetMapping("/page")
    public Result<PageResult<CostSupplement>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) Long contractId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, contractId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostSupplement> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostSupplement supplement) {
        return Result.ok(svc.create(supplement));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostSupplement supplement) {
        svc.update(supplement);
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
