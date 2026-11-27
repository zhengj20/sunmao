package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 认购
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_subscription")
public class SaleSubscription extends BaseEntity {

    private String subNo;
    private Long projectId;
    private Long roomId;
    private Long customerId;
    /** 定金金额 */
    private BigDecimal deposit;
    private Long agentId;
    private LocalDate expectedSignDate;
    /** DRAFT/APPROVING/ACTIVE/CONVERTED/REJECTED/TERMINATED */
    private String status;
    private Long contractId;

    /** 展示用：客户名称 */
    private transient String customerName;
    /** 展示用：房间编号 */
    private transient String roomCode;
}
