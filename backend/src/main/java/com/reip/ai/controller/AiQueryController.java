package com.reip.ai.controller;

import com.reip.ai.service.AiQueryService;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 指标问答（NL→指标语义层，R1）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiQueryController {

    private final AiQueryService svc;

    @GetMapping("/query")
    public Result<Map<String, Object>> query(@RequestParam String q,
                                             @RequestParam(required = false) Long projectId) {
        return Result.ok(svc.ask(q, projectId));
    }
}
