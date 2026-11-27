package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 委托代理（休假出差，审批留痕"代XX审批"）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wfl_entrust")
public class WflEntrust extends BaseEntity {
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;
    /** ALL或指定biz_type */
    private String bizScope;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    /** ACTIVE/CLOSED */
    private String status;
}
