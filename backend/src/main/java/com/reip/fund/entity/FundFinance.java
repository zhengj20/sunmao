package com.reip.fund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 融资台账（fund_finance）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_finance")
public class FundFinance extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 融资类型：开发贷/信托/债券 */
    private String financeType;

    /** 融资机构 */
    private String bankName;

    /** 融资额度 */
    private BigDecimal amount;

    /** 已用额度 */
    private BigDecimal usedAmount;

    /** 利率% */
    private BigDecimal rate;

    /** 放款日 */
    private LocalDate startDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 还款计划 JSON 字符串 */
    private String repayPlan;

    /** NORMAL/REPAID/OVERDUE */
    private String status;

    /** 备注 */
    private String remark;
}
