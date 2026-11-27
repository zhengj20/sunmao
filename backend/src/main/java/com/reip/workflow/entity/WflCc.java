package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批抄送（任务抄送 / 审批通过驳回时自动抄送发起人）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wfl_cc")
public class WflCc extends BaseEntity {
    private Long instanceId;
    private Long taskId;
    private Long ccUserId;
    private String ccUserName;
    private String content;
}
