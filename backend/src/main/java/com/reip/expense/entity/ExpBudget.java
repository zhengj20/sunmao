package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用预算（部门×科目×月）四栏：预算/占用/核销/余额
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_budget")
public class ExpBudget extends BaseEntity {

    private Long deptId;
    private Long accountId;
    private String accountName;
    private Integer budgetYear;
    private Integer month;
    /** 预算金额 */
    private BigDecimal amount;
    /** 已占用(生效申请) */
    private BigDecimal occupyAmount;
    /** 已核销(审批通过报销) */
    private BigDecimal writeoffAmount;
    /** 余额 = 预算 - 占用 - 核销 */
    private BigDecimal balance;
    /** ACTIVE/ARCHIVED */
    private String status;

    /** 展示用：部门名称 */
    private transient String deptName;
}
