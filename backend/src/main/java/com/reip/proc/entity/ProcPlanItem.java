package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购计划明细（来源采购需求）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_plan_item")
public class ProcPlanItem extends BaseEntity {

    /** 所属计划 */
    private Long planId;
    /** 来源采购需求 */
    private Long requirementId;
    /** 标的/需求标题 */
    private String title;
    /** 预估金额 */
    private BigDecimal estimateAmount;
    /** 期望时间 */
    private LocalDate expectDate;
    private String remark;
}
