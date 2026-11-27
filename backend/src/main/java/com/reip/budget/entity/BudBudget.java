package com.reip.budget.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 年度全面预算（成本线：项目×成本科目；费用线：部门×费用科目）
 * 管控链顶端：年度控制月度（B1），月度控制资金计划（B3/B4/B5）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudBudget extends BaseEntity {

    /** 预算编号 */
    private String budgetCode;
    /** 预算年度 */
    private Integer budgetYear;
    /** COST成本线 / EXPENSE费用线 */
    private String budgetType;
    /** 成本线：项目（费用线为NULL） */
    private Long projectId;
    /** 费用线：部门（成本线为NULL） */
    private Long deptId;
    /** 科目（mdm_cost_account 末级） */
    private Long accountId;
    /** 科目名（冗余） */
    private String accountName;
    /** 年度预算额 */
    private BigDecimal annualAmount;
    /** 已分解（Σ月度预算） */
    private BigDecimal allocatedAmount;
    /** 控制方式：刚性/预警/不限 */
    private String controlMode;
    /** DRAFT草稿 / APPROVED生效 / ARCHIVED归档 */
    private String status;
    private String remark;

    /** 展示用：项目名称 */
    @TableField(exist = false)
    private String projectName;
    /** 展示用：部门名称 */
    @TableField(exist = false)
    private String deptName;
    /** 展示用：已执行（年度） */
    @TableField(exist = false)
    private BigDecimal actualAmount;
    /** 展示用：执行率% */
    @TableField(exist = false)
    private BigDecimal execRate;
    /** 年度预算的月度分解（详情用） */
    @TableField(exist = false)
    private List<BudBudgetMonth> months;
}
