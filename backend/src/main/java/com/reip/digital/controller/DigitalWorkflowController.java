package com.reip.digital.controller;

import com.reip.common.result.Result;
import com.reip.digital.entity.DigWorkflow;
import com.reip.digital.entity.DigWorkflowRun;
import com.reip.digital.entity.DigWorkflowStep;
import com.reip.digital.service.DigitalWorkflowExecutor;
import com.reip.digital.service.DigitalWorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/digital/workflow")
@RequiredArgsConstructor
public class DigitalWorkflowController {

    private final DigitalWorkflowService svc;
    private final DigitalWorkflowExecutor executor;

    @Data
    public static class Payload {
        private DigWorkflow workflow;
        private List<DigWorkflowStep> steps;
    }

    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board() {
        return Result.ok(svc.board());
    }

    @PostMapping
    public Result<DigWorkflow> create(@RequestBody Payload p) {
        return Result.ok(svc.create(p.getWorkflow(), p.getSteps()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Payload p) {
        svc.update(id, p.getWorkflow(), p.getSteps());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/run")
    public Result<Long> run(@PathVariable Long id) {
        return Result.ok(executor.execute(id, "MANUAL"));
    }

    @GetMapping("/runs")
    public Result<List<DigWorkflowRun>> runs(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long wfId) {
        return Result.ok(svc.runs(current, size, wfId));
    }

    @GetMapping("/runs/{id}")
    public Result<DigWorkflowRun> runDetail(@PathVariable Long id) {
        return Result.ok(svc.runDetail(id));
    }

    // ==================== 人工审批 ====================

    /** 待审批列表（含工作流/环节/员工信息 + 上游产出预览） */
    @GetMapping("/approvals")
    public Result<List<Map<String, Object>>> approvals() {
        return Result.ok(svc.pendingApprovals());
    }

    /** 审批历史（最近已决策） */
    @GetMapping("/approvals/history")
    public Result<?> approvalHistory(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(svc.approvalHistory(limit));
    }

    /** 审批决策：decision=approve 继续执行 / reject 终止工作流 */
    @PostMapping("/approvals/{id}/decide")
    public Result<Void> decide(@PathVariable Long id, @RequestBody DecidePayload p) {
        if (p.decision() == null || (!"approve".equalsIgnoreCase(p.decision()) && !"reject".equalsIgnoreCase(p.decision()))) {
            return Result.fail("审批决策非法（approve/reject）");
        }
        svc.decide(id, p.decision(), p.comment());
        return Result.ok();
    }

    public record DecidePayload(String decision, String comment) {}
}
