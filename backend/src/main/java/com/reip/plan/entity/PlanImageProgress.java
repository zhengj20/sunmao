package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 形象进度确认单（供 cost 付款 R4 校验）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_image_progress")
public class PlanImageProgress extends BaseEntity {

    private Long projectId;
    private String building;
    /** 部位(基础/主体/装修/机电) */
    private String partName;
    /** 已确认完成% */
    private BigDecimal confirmedPct;
    private LocalDate confirmDate;
    private String imageIds;
    /** PENDING/CONFIRMED */
    private String status;
}
