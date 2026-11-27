package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_workflow_run")
public class DigWorkflowRun extends BaseEntity {
    private Long wfId;
    private String wfName;
    private String status;
    private Integer currentStep;
    private Integer totalSteps;
    /** 失败（含超时）的步骤数；0=全部成功 */
    private Integer failedSteps;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime finishedAt;
    private String steps;
    private String output;
    private String error;
}
