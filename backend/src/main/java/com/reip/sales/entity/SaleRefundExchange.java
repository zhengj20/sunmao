package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 退换申请（退定/退房/换房）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_refund_exchange")
public class SaleRefundExchange extends BaseEntity {

    private String refundNo;
    private Long projectId;
    /** 退房/换房关联合同 */
    private Long contractId;
    /** 退定关联认购 */
    private Long subscriptionId;
    private Long roomId;
    /** REFUND退定/退房,EXCHANGE换房 */
    private String type;
    private BigDecimal refundAmount;
    private String reason;
    private String reasonCategory;
    /** DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;

    /** 展示用：合同号 */
    private transient String contractNo;
    /** 展示用：房间编号 */
    private transient String roomCode;
}
