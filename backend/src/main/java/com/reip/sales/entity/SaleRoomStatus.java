package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源销控（一房一档）：同一 room_id 同一时刻仅一条有效(status<>'REFUNDED')
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_room_status")
public class SaleRoomStatus extends BaseEntity {

    private Long projectId;
    /** 关联 mdm_room */
    private Long roomId;
    private String roomCode;
    private Long buildingId;
    private Integer floorNo;
    private Integer unitNo;
    private BigDecimal area;
    /** 表价（总价） */
    private BigDecimal tablePrice;
    /** 底价（总价） */
    private BigDecimal floorPrice;
    /** SELLABLE/LOCKED/SMALL_DEPOSIT/BIG_DEPOSIT/SUBSCRIBED/CONTRACTED/FILED/REFUNDED */
    private String status;
    private Long lockBy;
    private LocalDateTime lockExpire;
    /** 关联单据号（认购/签约） */
    private String bizNo;

    /** 展示用：户型（来自 mdm_room） */
    private transient String layout;
    /** 展示用：楼栋名称 */
    private transient String buildingName;
}
