package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报销单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_reimburse")
public class ExpReimburse extends BaseEntity {

    private String reimbNo;
    /** TRAVEL差旅/DAILY日常 */
    private String type;
    private Long deptId;
    private Long userId;
    private String userName;
    private BigDecimal totalAmount;
    /** 超标额(实际-标准) R3 */
    private BigDecimal overAmount;
    /** 超标原因 R3 */
    private String overReason;
    private Long applyId;
    /** 冲销借款 */
    private Long loanId;
    /** 关联发票 */
    private Long invoiceId;
    /** DRAFT/APPROVING/APPROVED/REJECTED/PAID */
    private String status;

    /** 展示用：明细行（非持久化） */
    private transient List<ExpReimburseItem> items;
}
