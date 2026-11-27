package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款申请
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_payment_apply")
public class CostPaymentApply extends BaseEntity {

    /** 付款单编号（FK 规则生成） */
    private String paymentCode;
    /** 项目 */
    private Long projectId;
    /** 合同 */
    private Long contractId;
    /** 付款类型：PREPAY预付/PROGRESS进度/SETTLE结算/RETAIN质保 */
    private String paymentType;
    /** 申请金额 */
    private BigDecimal applyAmount;
    /** 累计已审金额（申请时点） */
    private BigDecimal appliedAmount;
    /** 本次关联发票额 */
    private BigDecimal invoiceAmount;
    /** 形象进度确认单（二期） */
    private Long imageProgressId;
    /** 形象进度% */
    private BigDecimal imageProgressPct;
    private String description;
    /** 状态：DRAFT/APPROVING/APPROVED/PAID/REJECTED */
    private String status;
    /** 审批通过金额 */
    private BigDecimal approvedAmount;
    /** 实付金额 */
    private BigDecimal paidAmount;
    private LocalDate paidDate;
}
