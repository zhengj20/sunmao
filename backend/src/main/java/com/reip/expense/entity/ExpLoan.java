package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 借款（额度控制 R4）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_loan")
public class ExpLoan extends BaseEntity {

    private String loanNo;
    private Long userId;
    private String userName;
    private BigDecimal amount;
    /** 未冲销余额 */
    private BigDecimal unwriteoffBalance;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    /** APPROVING/ACTIVE/WRITEOFF/DUE */
    private String status;
}
