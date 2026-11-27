package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识库文档（RAG，embedding 留占位）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_kb_document")
public class AiKbDocument extends BaseEntity {

    private String docCode;
    private String title;
    /** 制度 / 指标 / 案例 */
    private String category;
    private String content;
    /** 1=已向量化 */
    private Integer embeddingReady;
    private String status;
    private String wikiPath;
    private Integer wikiOrder;
}
