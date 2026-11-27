package com.reip.digital.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.digital.entity.DigTask;
import com.reip.digital.entity.DigTaskRun;
import com.reip.digital.mapper.DigitalMappers;
import com.reip.digital.service.DigitalDataFetcher;
import com.reip.digital.service.DigitalExecutor;
import com.reip.digital.service.DigitalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务定义（CRUD）+ 手动触发 + 执行记录（过程留痕查询）
 */
@RestController
@RequestMapping("/api/digital/task")
@RequiredArgsConstructor
public class DigitalTaskController {

    private final DigitalTaskService svc;
    private final DigitalExecutor executor;
    private final DigitalDataFetcher fetcher;
    private final DigitalMappers.TaskRunMapper runMapper;

    @GetMapping("/list")
    public Result<List<DigTask>> list(@RequestParam(required = false) Long employeeId) {
        return Result.ok(svc.list(employeeId));
    }

    @GetMapping("/{id}")
    public Result<DigTask> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody DigTask t) {
        return Result.ok(svc.create(t));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DigTask t) {
        svc.update(id, t);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 手动执行（异步，返回执行记录ID） */
    @PostMapping("/{id}/run")
    public Result<Long> run(@PathVariable Long id) {
        return Result.ok(executor.execute(id, "MANUAL"));
    }

    // ==================== 执行统计 ====================

    /** 执行概览：总量/成功率/平均耗时/LLM占比 + 近7天趋势 */
    @GetMapping("/runs/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(svc.stats());
    }

    /** 条件触发可用指标（按取数域），供任务表单按技能取数域过滤 */
    @GetMapping("/cond-metrics")
    public Result<Map<String, List<Map<String, String>>>> condMetrics() {
        return Result.ok(fetcher.condMetrics());
    }

    // ==================== 执行记录 ====================

    @GetMapping("/runs")
    public Result<PageResult<DigTaskRun>> runs(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "15") long size,
                                               @RequestParam(required = false) Long employeeId,
                                               @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DigTaskRun> qw = new LambdaQueryWrapper<>();
        if (employeeId != null) qw.eq(DigTaskRun::getEmployeeId, employeeId);
        if (status != null && !status.isBlank()) qw.eq(DigTaskRun::getStatus, status);
        qw.orderByDesc(DigTaskRun::getId);
        Page<DigTaskRun> p = runMapper.selectPage(new Page<>(current, size), qw);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    @GetMapping("/runs/{id}")
    public Result<DigTaskRun> runDetail(@PathVariable Long id) {
        return Result.ok(executor.runDetail(id));
    }
}
