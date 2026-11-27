package com.reip.invest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 货值供销存月度流水（inv_value_flow，无标准字段→普通实体）
 */
@Data
@TableName("inv_value_flow")
public class InvValueFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 月份 yyyy-MM */
    private String flowMonth;

    /** 已售（签约）货值 */
    private BigDecimal soldValue;

    /** 退房回流货值 */
    private BigDecimal refundValue;

    /** 新增供货货值 */
    private BigDecimal supplyValue;

    /** 期末存货货值 */
    private BigDecimal stockValue;

    private LocalDateTime createdAt;
}
