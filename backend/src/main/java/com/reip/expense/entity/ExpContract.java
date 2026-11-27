package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用合同（台账 + 付款跟踪）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_contract")
public class ExpContract extends BaseEntity {

    private String contractNo;
    private String name;
    private String supplier;
    private BigDecimal amount;
    /** 已付金额（实付登记回写） */
    private BigDecimal paidAmount;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    /** ACTIVE/EXPIRED/TERMINATED */
    private String status;
    private String remark;
}
