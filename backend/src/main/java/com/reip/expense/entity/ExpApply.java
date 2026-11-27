package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用事前申请
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_apply")
public class ExpApply extends BaseEntity {

    private String applyNo;
    /** TRAVEL出差/HOST招待/PROCURE采购 */
    private String type;
    private Long deptId;
    private Long userId;
    private String userName;
    private BigDecimal amount;
    private Long budgetId;
    /** 实际占用预算额 */
    private BigDecimal occupyAmount;
    private String remark;
    /** DRAFT/APPROVING/ACTIVE/REJECTED */
    private String status;
}
