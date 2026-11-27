package com.reip.ai.controller;

import com.reip.ai.entity.AiAuditLog;
import com.reip.ai.service.AiAuditService;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用审计日志
 */
@RestController
@RequestMapping("/api/ai/audit")
@RequiredArgsConstructor
public class AiAuditController {

    private final AiAuditService svc;

    @GetMapping
    public Result<PageResult<AiAuditLog>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String scene,
                                               @RequestParam(required = false) String aiMode,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, scene, aiMode, status));
    }

    @GetMapping("/{id}")
    public Result<AiAuditLog> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }
}
