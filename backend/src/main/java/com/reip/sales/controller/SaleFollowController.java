package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleFollow;
import com.reip.sales.service.SaleFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户跟进：客户池概览 / 跟进记录CRUD / 逾期跟进提醒
 */
@RestController
@RequestMapping("/api/sales/follow")
@RequiredArgsConstructor
public class SaleFollowController {

    private final SaleFollowService svc;

    /** 跟进记录分页 */
    @GetMapping("/page")
    public Result<PageResult<SaleFollow>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String dealStatus,
                                               @RequestParam(required = false) String intentLevel,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, dealStatus, intentLevel, kw));
    }

    /** 客户池概览 KPI */
    @GetMapping("/pool")
    public Result<Map<String, Object>> pool(@RequestParam Long projectId) {
        return Result.ok(svc.pool(projectId));
    }

    /** 逾期跟进清单 */
    @GetMapping("/overdue")
    public Result<List<SaleFollow>> overdue(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.overdueList(projectId));
    }

    @GetMapping("/{id}")
    public Result<SaleFollow> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleFollow follow) {
        return Result.ok(svc.create(follow));
    }

    @PutMapping
    public Result<Void> update(@RequestBody SaleFollow follow) {
        svc.update(follow);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
