package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款计划（按合同条款维护）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_payment_plan")
public class CostPaymentPlan extends BaseEntity {

    /** 所属合同 */
    private Long contractId;
    /** 期数 */
    private Integer termNo;
    /** 期次描述 */
    private String termDesc;
    /** 计划金额 */
    private BigDecimal planAmount;
    private LocalDate planDate;
    /** 已付金额 */
    private BigDecimal paidAmount;
}
