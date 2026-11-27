package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收计划（按合同付款方式生成）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_payment_plan")
public class SalePaymentPlan extends BaseEntity {

    private Long contractId;
    private Integer seq;
    private String termDesc;
    /** 应收金额 */
    private BigDecimal planAmount;
    /** 已收金额（核销） */
    private BigDecimal receivedAmount;
    private LocalDate dueDate;
    /** DUE待收/RECEIVED已收/OVERDUE逾期 */
    private String status;

    /** 展示用：合同号 */
    private transient String contractNo;
}
