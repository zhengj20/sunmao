package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收票登记（发票池）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_invoice")
public class CostInvoice extends BaseEntity {

    private Long projectId;
    private Long contractId;
    /** 关联付款单 */
    private Long paymentId;
    private Long supplierId;
    /** 发票号（全平台唯一查重） */
    private String invoiceNo;
    /** 增值税专用/普通 */
    private String invoiceType;
    /** 价税合计 */
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private LocalDate invoiceDate;
    /** 查验状态：UNCHECKED/PASSED/FAILED */
    private String checkStatus;
    /** 查重hash（全平台发票池） */
    private String duplicateHash;
}
