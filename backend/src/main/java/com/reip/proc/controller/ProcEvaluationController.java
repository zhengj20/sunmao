package com.reip.proc.controller;

import com.reip.common.result.Result;
import com.reip.proc.entity.ProcEvalDetail;
import com.reip.proc.entity.ProcEvaluation;
import com.reip.proc.service.ProcEvaluationService;
import com.reip.proc.service.ProcEvaluationService.ScoreRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评标
 */
@RestController
@RequestMapping("/api/proc/evaluations")
@RequiredArgsConstructor
public class ProcEvaluationController {

    private final ProcEvaluationService svc;

    @GetMapping
    public Result<List<ProcEvaluation>> list(@RequestParam Long procProjectId) {
        return Result.ok(svc.list(procProjectId));
    }

    @GetMapping("/{id}")
    public Result<ProcEvaluation> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @GetMapping("/{id}/details")
    public Result<List<ProcEvalDetail>> details(@PathVariable Long id) {
        return Result.ok(svc.details(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcEvaluation evaluation) {
        return Result.ok(svc.create(evaluation));
    }

    /** 专家评分：写明细留痕，回标技术分取均值 */
    @PostMapping("/{id}/score")
    public Result<Void> score(@PathVariable Long id, @RequestBody ScoreRequest req) {
        svc.score(id, req);
        return Result.ok();
    }

    /** 自动排名并生成评标结论：SCORING→REPORTED */
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        svc.finish(id);
        return Result.ok();
    }
}
