package com.reip.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度资金计划（fund_plan，无标准字段→普通实体）
 */
@Data
@TableName("fund_plan")
public class FundPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划月份 yyyy-MM */
    private String planMonth;

    /** 项目ID（NULL=集团汇总） */
    private Long projectId;

    /** DRAFT初稿 / FINAL定稿 */
    private String versionType;

    /** 计划收款（销售应收+按揭） */
    private BigDecimal receiptPlan;

    /** 计划付款（成本+费用+税费） */
    private BigDecimal paymentPlan;

    /** 净流入计划 */
    private BigDecimal netPlan;

    /** 实际收款 */
    private BigDecimal receiptActual;

    /** 实际付款 */
    private BigDecimal paymentActual;

    /** 资金缺口 */
    private BigDecimal gapAmount;

    /** 明细构成 JSON */
    private String detailJson;

    private LocalDateTime createdAt;

    /** 项目名称（联表展示，非表字段） */
    @TableField(exist = false)
    private String projectName;
}
