package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt")
public class AiPrompt extends BaseEntity {

    private String promptCode;
    private String name;
    /** APPROVAL_PREVIEW / QUERY / REPORT / QA */
    private String scene;
    /** 提示词正文，{{key}} 占位符 */
    private String templateText;
    private Integer promptVersion;
    private String status;
}
