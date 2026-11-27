package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合约规划明细（科目-规划-合同三联穿透）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_plan_item")
public class CostPlanItem extends BaseEntity {

    /** 所属规划 */
    private Long planId;
    /** 项目 */
    private Long projectId;
    /** 成本科目 */
    private Long accountId;
    /** 科目编码（从科目树带出） */
    private String accountCode;
    /** 科目名称（从科目树带出） */
    private String accountName;
    /** 规划金额 */
    private BigDecimal plannedAmount;
    /** 已签约占用（合同生效回写） */
    private BigDecimal contractedAmount;
    /** 在途占用（审批中合同） */
    private BigDecimal pendingAmount;
    /** 范围描述 */
    private String scope;
    /** 拟签时间 */
    private LocalDate expectedSignDate;
    private String remark;

    /** 余量=规划金额-已签约-在途（实时计算返回，非表字段） */
    @TableField(exist = false)
    private BigDecimal remainingAmount;
}
