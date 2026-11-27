package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 事项申请（通用事项审批单，以"事"为中心）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_matter")
public class ExpMatter extends BaseEntity {

    private String matterNo;
    /** MEETING会议/ACTIVITY活动/RENOVATE装修/PROCURE采购/OFFICE办公/OTHER其他 */
    private String type;
    private String title;
    private String description;
    private Long deptId;
    private Long userId;
    private String userName;
    /** 预计金额（可选） */
    private BigDecimal expectAmount;
    /** 计划日期（可选） */
    private LocalDate planDate;
    /** DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;
}
