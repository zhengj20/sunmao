package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 大模型配置（密钥加密存储，展示脱敏）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    /** OPENAI_COMPATIBLE / QWEN / DEEPSEEK */
    private String provider;
    private String modelName;
    /** OpenAI 兼容 base_url（含 /v1） */
    private String baseUrl;
    /** API Key（AES 加密后存储；响应中置空，仅返回 apiKeyMasked） */
    private String apiKey;
    private Integer timeoutMs;
    private BigDecimal temperature;
    /** ACTIVE / INACTIVE */
    private String status;
    private String remark;

    /** 展示用：脱敏后的密钥（非表字段） */
    private transient String apiKeyMasked;
}
