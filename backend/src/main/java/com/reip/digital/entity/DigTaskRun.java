package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行记录：过程留痕，steps 为 JSON 数组 [{seq,title,status,detail,ts}]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_task_run")
public class DigTaskRun extends BaseEntity {

    private String runNo;
    private Long taskId;
    private Long employeeId;
    private String taskName;
    private String skillName;
    /** MANUAL/SCHEDULED/CONDITIONAL */
    private String triggerType;
    /** RUNNING/DONE/FAILED */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String modelName;
    /** LLM/RULE */
    private String aiMode;
    /** LLM 输入 tokens */
    private Integer tokenIn;
    /** LLM 输出 tokens */
    private Integer tokenOut;
    private String output;
    private String steps;
    private String error;
}
