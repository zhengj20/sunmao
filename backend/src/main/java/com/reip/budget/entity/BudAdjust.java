package com.reip.budget.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算调整单（年度/月度调整留痕）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudAdjust extends BaseEntity {

    /** 调整单号 */
    private String adjustCode;
    /** 年度预算 */
    private Long budgetId;
    /** NULL=年度调整，非空=月度调整 */
    private Long monthId;
    /** INCREASE调增 / DECREASE调减 */
    private String adjustType;
    /** 调整金额 */
    private BigDecimal amount;
    /** 调整原因 */
    private String reason;
    /** APPROVED已生效 / REJECTED已驳回（预留审批） */
    private String status;

    /** 展示用：预算名称（科目+年度） */
    @TableField(exist = false)
    private String budgetName;
    /** 展示用：月份（月度调整时） */
    @TableField(exist = false)
    private Integer month;
}
