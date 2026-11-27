package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流人工审批门：环节勾选「需人工审批」时，执行到此暂停等待决策
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_wf_approval")
public class DigWfApproval extends BaseEntity {

    private Long wfRunId;
    private Integer stepOrder;
    private String stepName;
    /** PENDING/APPROVED/REJECTED */
    private String status;
    private String comment;
    private Long decidedBy;
    private LocalDateTime decidedAt;
}
