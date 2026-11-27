package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目计划任务（WBS 实例，主项+专项）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_task")
public class PlanTask extends BaseEntity {

    private Long projectId;
    /** 父节点(WBS) */
    private Long parentId;
    private String taskCode;
    private String taskName;
    /** 里程碑/一级/二级/三级 */
    private String level;
    /** 1里程碑 0普通 */
    private Integer isMilestone;
    /** 专业(设计/报建/工程/营销/交付) */
    private String specialty;
    private LocalDate planStart;
    private LocalDate planEnd;
    /** 基线(预警基准) */
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
    private LocalDate actualStart;
    private LocalDate actualEnd;
    private BigDecimal progressPct;
    private Long respOrgId;
    private Long respUserId;
    private String respUserName;
    /** 前置依赖任务ID(逗号分隔) */
    private String preTaskIds;
    /** GREEN/YELLOW/RED */
    private String alertStatus;
    /** PLANNED/IN_PROGRESS/COMPLETED/DELAYED */
    private String status;
}
