package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务定义：定时(SCHEDULED)/条件(CONDITIONAL)/手动(MANUAL)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_task")
public class DigTask extends BaseEntity {

    private String taskCode;
    private Long employeeId;
    private Long skillId;
    private String name;
    /** SCHEDULED/CONDITIONAL/MANUAL */
    private String triggerType;
    /** CRON(6段含秒) */
    private String cronExpr;
    /** 条件JSON: {"source":"指标key","op":">=","value":1} */
    private String conditionExpr;
    /** 条件触发最小间隔(分钟) */
    private Integer cooldownMinutes;
    private String status;
    private LocalDateTime lastRunAt;
    /** 定时任务下次执行时间 */
    private LocalDateTime nextRunAt;
    private String remark;
}
