package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售合同
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_contract")
public class SaleContract extends BaseEntity {

    private String contractNo;
    private Long projectId;
    private Long roomId;
    private Long customerId;
    /** 成交价 */
    private BigDecimal contractPrice;
    /** ONE_TIME一次性/MORTGAGE按揭/INSTALLMENT分期 */
    private String payMethod;
    /** 成交折扣率（成交价/表价） */
    private BigDecimal discountRate;
    private Long subscriptionId;
    /** 中介（供应商） */
    private Long supplierId;
    private String supplierName;
    private LocalDate signedAt;
    /** DRAFT/APPROVING/ACTIVE/FILED/REFUNDED */
    private String status;

    /** 展示用：客户名称 */
    private transient String customerName;
    /** 展示用：房间编号 */
    private transient String roomCode;
    /** 展示用：回款率% */
    private transient BigDecimal collectionRate;
}
