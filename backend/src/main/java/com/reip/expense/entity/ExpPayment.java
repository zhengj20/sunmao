package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销付款单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_payment")
public class ExpPayment extends BaseEntity {

    private String payNo;
    private Long reimbId;
    /** 关联费用合同（可选，付款跟踪） */
    private Long contractId;
    private BigDecimal amount;
    private LocalDate paidDate;
    /** DRAFT/PAID */
    private String status;
}
