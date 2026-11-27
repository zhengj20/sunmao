package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 调用审计日志（R3 留痕）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_audit_log")
public class AiAuditLog extends BaseEntity {

    /** APPROVAL_PREVIEW / QUERY / REPORT / QA / MODEL_TEST */
    private String scene;
    private Long userId;
    private Long modelConfigId;
    /** 输入脱敏摘要 */
    private String inputDigest;
    /** 输出脱敏摘要 */
    private String outputDigest;
    private Integer tokenIn;
    private Integer tokenOut;
    private BigDecimal cost;
    private Long durationMs;
    /** RULE / LLM */
    private String aiMode;
    /** SUCCESS / FAIL / DEGRADED */
    private String status;
}
