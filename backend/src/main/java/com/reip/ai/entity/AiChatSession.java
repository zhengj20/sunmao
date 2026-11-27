package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 聊天会话
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_session")
public class AiChatSession extends BaseEntity {

    /** 会话业务 ID（UUID） */
    private String sessionId;
    private Long userId;
    private String title;
}
