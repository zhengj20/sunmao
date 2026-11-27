package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 计划调整单（时间变更审批）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_adjust")
public class PlanAdjust extends BaseEntity {

    private String adjustNo;
    private Long taskId;
    private String reason;
    /** 延期原因归类(政策/资金/施工/营销/其他) */
    private String reasonCategory;
    private LocalDate oldStart;
    private LocalDate oldEnd;
    private LocalDate newStart;
    private LocalDate newEnd;
    private String impactAnalysis;
    /** DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;

    /** 展示用：任务名称（非持久化） */
    private transient String taskName;
}
