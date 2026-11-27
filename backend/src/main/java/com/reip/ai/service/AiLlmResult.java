package com.reip.ai.service;

import lombok.Data;

/**
 * LLM 网关调用结果。
 * degraded=true 表示未配置模型或调用失败 → 调用方应降级到规则引擎（R4）。
 */
@Data
public class AiLlmResult {

    private boolean success;
    /** true=应降级（无模型/失败） */
    private boolean degraded;
    private String content;
    private String model;
    private Integer tokenIn;
    private Integer tokenOut;
    private Long durationMs;
    private String message;

    public static AiLlmResult none() {
        AiLlmResult r = new AiLlmResult();
        r.degraded = true;
        r.success = false;
        r.message = "未配置可用大模型，已降级为规则引擎";
        return r;
    }

    public static AiLlmResult fail(String message) {
        AiLlmResult r = new AiLlmResult();
        r.degraded = true;
        r.success = false;
        r.message = message == null ? "大模型调用失败" : message;
        return r;
    }

    public static AiLlmResult ok(String content, String model, Integer tokenIn, Integer tokenOut, Long durationMs) {
        AiLlmResult r = new AiLlmResult();
        r.success = true;
        r.degraded = false;
        r.content = content;
        r.model = model;
        r.tokenIn = tokenIn;
        r.tokenOut = tokenOut;
        r.durationMs = durationMs;
        return r;
    }
}
