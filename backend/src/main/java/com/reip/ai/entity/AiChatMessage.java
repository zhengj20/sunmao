package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 聊天消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class AiChatMessage extends BaseEntity {

    private String sessionId;
    /** USER / ASSISTANT */
    private String role;
    private String content;
    /** RULE / LLM */
    private String aiMode;
    /** 引用单据/指标 JSON */
    private String refs;
}
