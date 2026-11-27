package com.reip.budget.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 月度全面预算（年度预算的月度分解）
 * B1：Σ月度预算 ≤ 年度预算额
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudBudgetMonth extends BaseEntity {

    /** 年度预算 */
    private Long budgetId;
    /** 1-12 */
    private Integer month;
    /** 月度预算额 */
    private BigDecimal monthAmount;
    /** ACTIVE/INACTIVE */
    private String status;

    /** 展示用：已执行（当月） */
    @TableField(exist = false)
    private BigDecimal actualAmount;
    /** 展示用：余额 = 月度预算 - 已执行 */
    @TableField(exist = false)
    private BigDecimal balance;
    /** 展示用：超预算标记 */
    @TableField(exist = false)
    private Boolean overBudget;
}
