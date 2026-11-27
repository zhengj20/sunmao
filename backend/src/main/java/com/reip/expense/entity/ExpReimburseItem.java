package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报销明细行
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_reimburse_item")
public class ExpReimburseItem extends BaseEntity {

    private Long reimbId;
    private Long accountId;
    private String accountName;
    private BigDecimal amount;
    private String remark;
}
