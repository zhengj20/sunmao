package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 折扣方案（分级权限）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_discount")
public class SaleDiscount extends BaseEntity {

    private String discountNo;
    private String name;
    private String scope;
    /** 基准折扣率 */
    private BigDecimal baseRate;
    /** 该级别最大折扣率（可下探下限） */
    private BigDecimal maxRate;
    /** AGENT置业顾问/MGR经理/GM总经理 */
    private String level;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
}
