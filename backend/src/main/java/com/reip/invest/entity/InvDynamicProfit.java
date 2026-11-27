package com.reip.invest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动态利润月度快照（inv_dynamic_profit，无标准字段→普通实体）
 */
@Data
@TableName("inv_dynamic_profit")
public class InvDynamicProfit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 快照月份 yyyy-MM */
    private String snapMonth;

    /** 动态货值 */
    private BigDecimal dynamicValue;

    /** 动态成本（取最新动态成本快照） */
    private BigDecimal dynamicCost;

    /** 动态三费 */
    private BigDecimal threeFee;

    /** 税金 */
    private BigDecimal taxAmount;

    /** 动态利润=货值-成本-三费-税金 */
    private BigDecimal dynamicProfit;

    /** 利润率% */
    private BigDecimal profitRate;

    /** 拿地测算利润（对照基线） */
    private BigDecimal baselineProfit;

    /** 明细 JSON */
    private String detailJson;

    private LocalDateTime createdAt;
}
