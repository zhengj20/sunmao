package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 发票池（全集团进项：报销/成本/采购）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_invoice")
public class ExpInvoice extends BaseEntity {

    private String invNo;
    /** 发票代码/税号 */
    private String taxNo;
    private String supplier;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    /** UNVERIFIED/PASS/FAIL */
    private String verifyStatus;
    /** 查重hash(全平台唯一) R2 */
    private String duplicateHash;
    /** REIMBURSE/COST/PROC */
    private String source;
    /** ACTIVE/VOID */
    private String status;
}
