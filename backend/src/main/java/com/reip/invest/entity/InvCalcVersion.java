package com.reip.invest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 投资测算版本（inv_calc_version）
 * 锁定后作为后评估基准，禁止修改
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_calc_version")
public class InvCalcVersion extends BaseEntity {

    /** 版本编码（CS-前缀自动生成） */
    private String versionCode;

    /** 项目ID */
    private Long projectId;

    /** 阶段：LAND拿地测算 / UPDATE调整版 */
    private String stage;

    /** 总货值 */
    private BigDecimal saleValue;

    /** 总成本（土地+开发） */
    private BigDecimal totalCost;

    /** 三费 */
    private BigDecimal threeFee;

    /** 税金 */
    private BigDecimal taxAmount;

    /** 净利润 */
    private BigDecimal netProfit;

    /** 净利率%（自动=净利润/总货值×100） */
    private BigDecimal profitRate;

    /** IRR% */
    private BigDecimal irr;

    /** 回收期（年） */
    private BigDecimal paybackYears;

    /** 测算明细 JSON */
    private String detailJson;

    /** 投资决策会记录 */
    private String decisionNotes;

    /** 基线锁定：0否 1是 */
    private Integer locked;

    /** DRAFT / LOCKED */
    private String status;
}
