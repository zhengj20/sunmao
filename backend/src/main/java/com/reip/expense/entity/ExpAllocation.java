package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用分摊
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_allocation")
public class ExpAllocation extends BaseEntity {

    private String allocNo;
    /** 分摊月(yyyy-MM) */
    private String month;
    /** 分摊规则文本 */
    private String ruleText;
    private Long deptId;
    private Long projectId;
    private BigDecimal amount;
}
