package com.reip.ai.controller;

import com.reip.ai.service.AiApprovalService;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审批预审（AI 数字员工 P0）
 */
@RestController
@RequestMapping("/api/ai/approval")
@RequiredArgsConstructor
public class AiApprovalController {

    private final AiApprovalService svc;

    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam(required = false) String bizType,
                                               @RequestParam Long bizId) {
        return Result.ok(svc.preview(bizType, bizId));
    }
}
