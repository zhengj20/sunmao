package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 目标成本明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_target_item")
public class CostTargetItem extends BaseEntity {

    /** 所属版本 */
    private Long versionId;
    /** 成本科目 */
    private Long accountId;
    /** 科目编码（从科目树带出） */
    private String accountCode;
    /** 科目名称（从科目树带出） */
    private String accountName;
    /** 工程量 */
    private BigDecimal quantity;
    /** 单位 */
    private String unit;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 金额=量×价（自动计算） */
    private BigDecimal amount;
    /** 单方成本=金额/版本建筑面积 */
    private BigDecimal unitCost;
    /** 引用指标来源 */
    private String indicatorRef;
    private String remark;
}
