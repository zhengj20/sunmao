package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实收登记
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_receipt")
public class SaleReceipt extends BaseEntity {

    private String receiptNo;
    private Long contractId;
    private Long planId;
    private BigDecimal receivedAmount;
    /** POS/转账/现金 */
    private String payWay;
    private String ticketNo;
    private LocalDateTime receivedAt;

    /** 展示用：合同号 */
    private transient String contractNo;
}
