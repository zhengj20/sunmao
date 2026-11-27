package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 成本指标库（project_id 为空表示集团指标）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_indicator")
public class CostIndicator extends BaseEntity {

    /** 项目（NULL=集团指标库） */
    private Long projectId;
    /** 科目编码 */
    private String accountCode;
    /** 业态 */
    private String productCategory;
    /** 指标类型：COST单方/CONTENT含量 */
    private String indicatorType;
    /** 指标名，如 钢筋含量 */
    private String indicatorName;
    /** 单位：元/㎡ 或 kg/㎡ */
    private String unit;
    private BigDecimal value;
    /** 来源项目/版本 */
    private String source;
}
