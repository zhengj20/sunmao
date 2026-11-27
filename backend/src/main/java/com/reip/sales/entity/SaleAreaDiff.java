package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 面积补差（多退少补）：合同面积 vs 实测面积，按单价生成差价款
 * direction：COLLECT=少补(需向客户收款) / REFUND=多退(需向客户退款) / NONE=无差异
 * status：PENDING待结算 / GENERATED已生成补差单 / SETTLED已结清
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_area_diff")
public class SaleAreaDiff extends BaseEntity {

    private String diffNo;
    private Long projectId;
    private Long contractId;
    private Long roomId;
    private String roomCode;
    private Long customerId;
    private String customerName;

    /** 合同面积㎡ */
    private BigDecimal contractArea;
    /** 实测面积㎡ */
    private BigDecimal measuredArea;
    /** 面积差㎡（实测-合同） */
    private BigDecimal diffArea;
    /** 补差单价(元/㎡) */
    private BigDecimal unitPrice;
    /** 差价款(正=少补需收款, 负=多退需退款) */
    private BigDecimal diffAmount;
    /** COLLECT少补/REFUND多退/NONE无差异 */
    private String direction;
    /** 误差免赔线(默认3%) */
    private BigDecimal threshold;
    /** 是否3%误差范围内(仅提示) */
    private Boolean withinTolerance;
    /** MANUAL手工/IMPORT导入 */
    private String source;
    private String batchNo;
    /** PENDING待结算/GENERATED已生成/SETTLED已结清 */
    private String status;
    /** 已结算金额 */
    private BigDecimal settleAmount;
    /** 少补收款生成的应收计划ID */
    private Long planId;
    private String remark;
}
