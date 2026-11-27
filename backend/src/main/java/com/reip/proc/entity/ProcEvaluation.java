package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 评标（综合评估/最低价/性价比）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_evaluation")
public class ProcEvaluation extends BaseEntity {

    /** 招采立项 */
    private Long procProjectId;
    /** COMPREHENSIVE综合评估/LOWEST最低价/COST_PERF性价比 */
    private String method;
    /** 技术权重% */
    private BigDecimal techWeight;
    /** 商务权重% */
    private BigDecimal bizWeight;
    /** 评标专家（逗号分隔） */
    private String expertNames;
    /** SCORING评分中/REPORTED评标报告已出 */
    private String status;
    /** 评标结论（汇总前二名） */
    private String conclusion;
}
