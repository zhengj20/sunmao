package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 超时记录（无标准字段，普通实体）
 */
@Data
@TableName("wfl_timeout_log")
public class WflTimeoutLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long instanceId;
    private String nodeName;
    private Long assigneeId;
    private Integer timeoutHours;
    /** REMIND/ESCALATE */
    private String actionTaken;
    private LocalDateTime createdAt;
}
