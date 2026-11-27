package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.PlanNodeLib;
import com.reip.plan.entity.PlanTemplate;
import com.reip.plan.service.PlanTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计划标准库：模板维护 + 节点库(WBS标准节点)
 */
@RestController
@RequestMapping("/api/plan/templates")
@RequiredArgsConstructor
public class PlanTemplateController {

    private final PlanTemplateService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String bizType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, bizType, status, kw));
    }

    @GetMapping("/{id}")
    public Result<PlanTemplate> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody PlanTemplate t) {
        return Result.ok(svc.create(t));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PlanTemplate t) {
        svc.update(id, t);
        return Result.ok();
    }

    // ==================== 节点库 ====================

    @GetMapping("/{id}/nodes")
    public Result<List<PlanNodeLib>> nodes(@PathVariable Long id) {
        return Result.ok(svc.nodes(id));
    }

    @PostMapping("/nodes")
    public Result<Long> addNode(@RequestParam Long templateId, @RequestBody PlanNodeLib n) {
        return Result.ok(svc.addNode(templateId, n));
    }

    @PutMapping("/nodes/{nodeId}")
    public Result<Void> updateNode(@PathVariable Long nodeId, @RequestBody PlanNodeLib n) {
        svc.updateNode(nodeId, n);
        return Result.ok();
    }

    @DeleteMapping("/nodes/{nodeId}")
    public Result<Void> deleteNode(@PathVariable Long nodeId) {
        svc.deleteNode(nodeId);
        return Result.ok();
    }
}
