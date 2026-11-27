package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.PlanImageProgress;
import com.reip.plan.service.PlanImageProgressService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 形象进度确认单 + 供 cost 进度款 R4 校验
 */
@RestController
@RequestMapping("/api/plan/image-progress")
@RequiredArgsConstructor
public class PlanImageProgressController {

    private final PlanImageProgressService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanImageProgress>> page(@RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "20") long size,
                                                       @RequestParam(required = false) Long projectId,
                                                       @RequestParam(required = false) String building,
                                                       @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, building, status));
    }

    @GetMapping("/{id}")
    public Result<PlanImageProgress> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody PlanImageProgress i) {
        return Result.ok(svc.create(i));
    }

    /** 确认形象进度 */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        svc.confirm(id);
        return Result.ok();
    }

    /** 已确认形象进度清单（供 cost 侧关联选择） */
    @GetMapping("/confirmed")
    public Result<List<PlanImageProgress>> confirmed(@RequestParam Long projectId,
                                                      @RequestParam(required = false) String building) {
        return Result.ok(svc.confirmedList(projectId, building));
    }

    /** 查询已确认%（供 cost 侧带出） */
    @GetMapping("/pct")
    public Result<BigDecimal> pct(@RequestParam Long projectId,
                                  @RequestParam String building,
                                  @RequestParam String partName) {
        return Result.ok(svc.confirmedPct(projectId, building, partName));
    }

    /** R4 校验（供 cost 进度款申请调用） */
    @PostMapping("/validate")
    public Result<Void> validate(@RequestBody ValidateForm form) {
        svc.validateForPayment(form.getProjectId(), form.getBuilding(), form.getPartName(), form.getApplyPct());
        return Result.ok();
    }

    @Data
    public static class ValidateForm {
        private Long projectId;
        private String building;
        private String partName;
        private BigDecimal applyPct;
    }
}
