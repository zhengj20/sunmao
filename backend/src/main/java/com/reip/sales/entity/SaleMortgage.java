package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按揭
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_mortgage")
public class SaleMortgage extends BaseEntity {

    private Long contractId;
    private String bankName;
    private BigDecimal mortgageAmount;
    /** APPLY申请/APPROVE审批/MORTGAGE抵押/DISBURSE放款 */
    private String stage;
    private LocalDate disbursedAt;

    /** 展示用：合同号 */
    private transient String contractNo;
}
