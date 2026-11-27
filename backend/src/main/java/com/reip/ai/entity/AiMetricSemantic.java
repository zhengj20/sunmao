package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 指标语义映射（NL→指标，R1 语义层）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_metric_semantic")
public class AiMetricSemantic extends BaseEntity {

    /** 关联 metric_def.metric_code */
    private String metricCode;
    private String metricName;
    /** 分词/同义词，逗号分隔 */
    private String keywords;
    private String description;
}
