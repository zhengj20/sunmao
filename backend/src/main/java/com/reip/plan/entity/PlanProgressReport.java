package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进度填报（周报）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_progress_report")
public class PlanProgressReport extends BaseEntity {

    private Long projectId;
    /** 报告周(yyyy-ww) */
    private String reportWeek;
    private Long taskId;
    private BigDecimal progressPct;
    private String description;
    private LocalDate actualStart;
    private LocalDate actualEnd;
    private String imageIds;
    /** SUBMITTED/CONFIRMED */
    private String status;

    /** 展示用：任务名称（非持久化） */
    private transient String taskName;
}
