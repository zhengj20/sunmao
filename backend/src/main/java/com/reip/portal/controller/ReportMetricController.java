package com.reip.portal.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.portal.entity.CockpitMetricDef;
import com.reip.portal.service.ReportMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标字典 /api/report/metrics（CRUD + 列表）
 */
@RestController
@RequestMapping("/api/report/metrics")
@RequiredArgsConstructor
public class ReportMetricController {

    private final ReportMetricService svc;

    @GetMapping("/page")
    public Result<PageResult<CockpitMetricDef>> page(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String dimension,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, dimension, status, kw));
    }

    @GetMapping
    public Result<List<CockpitMetricDef>> list(@RequestParam(required = false) String dimension,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.list(dimension, status));
    }

    @GetMapping("/{id}")
    public Result<CockpitMetricDef> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CockpitMetricDef m) {
        return Result.ok(svc.create(m));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CockpitMetricDef m) {
        svc.update(id, m);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
