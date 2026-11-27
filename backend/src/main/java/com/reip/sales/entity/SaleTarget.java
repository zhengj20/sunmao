package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售目标（月度/季度/年度 + 金额/套数/面积口径，可细分到顾问）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_target")
public class SaleTarget extends BaseEntity {

    private Long projectId;
    /** MONTH月/QUARTER季/YEAR年 */
    private String periodType;
    /** 周期(2026-09 / 2026-Q3 / 2026) */
    private String period;
    /** AMOUNT金额/COUNT套数/AREA面积 */
    private String metric;
    /** 目标值 */
    private BigDecimal targetValue;
    /** 细分到置业顾问（空=项目整体） */
    private Long agentId;
    /** ACTIVE/INACTIVE */
    private String status;

    /** 展示用：顾问姓名 */
    private transient String agentName;
    /** 展示用：实际完成值（按 metric 口径从成交/回款聚合） */
    private transient BigDecimal actualValue;
    /** 展示用：达成率% */
    private transient BigDecimal achievementRate;
}
