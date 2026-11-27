package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 异步任务中心
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_task")
public class AiTask extends BaseEntity {

    private String taskCode;
    /** OCR / REPORT / PREVIEW */
    private String type;
    /** PENDING / RUNNING / DONE / FAILED */
    private String status;
    private String title;
    private String input;
    private String output;
}
