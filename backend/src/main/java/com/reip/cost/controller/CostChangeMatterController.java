package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostChange;
import com.reip.cost.entity.CostChangeMatter;
import com.reip.cost.service.CostChangeMatterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 变更事项申请：签证/设计变更的上游申请，1事项→N变更
 */
@RestController
@RequestMapping("/api/cost/changes/matters")
@RequiredArgsConstructor
public class CostChangeMatterController {

    private final CostChangeMatterService svc;

    @GetMapping("/page")
    public Result<PageResult<CostChangeMatter>> page(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, status, kw));
    }

    @GetMapping("/{id}")
    public Result<CostChangeMatter> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 事项 + 关联变更 + 金额汇总（详情页） */
    @GetMapping("/{id}/summary")
    public Result<Map<String, Object>> summary(@PathVariable Long id) {
        return Result.ok(svc.summary(id));
    }

    /** 事项下的变更列表 */
    @GetMapping("/{id}/changes")
    public Result<List<CostChange>> linkedChanges(@PathVariable Long id) {
        return Result.ok(svc.linkedChanges(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CostChangeMatter matter) {
        return Result.ok(svc.create(matter));
    }

    @PutMapping
    public Result<Void> update(@RequestBody CostChangeMatter matter) {
        svc.update(matter);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        svc.approve(id);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        svc.close(id);
        return Result.ok();
    }
}
