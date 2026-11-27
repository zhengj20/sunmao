package com.reip.ai.controller;

import com.reip.ai.entity.AiTask;
import com.reip.ai.service.AiReportService;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报告生成
 */
@RestController
@RequestMapping("/api/ai/report")
@RequiredArgsConstructor
public class AiReportController {

    private final AiReportService svc;

    @GetMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestParam String type,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String month) {
        return Result.ok(svc.generate(type, projectId, month));
    }

    @GetMapping("/list")
    public Result<PageResult<AiTask>> list(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(svc.list(current, size));
    }

    @GetMapping("/{id}")
    public Result<AiTask> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }
}
