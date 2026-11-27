package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 会议决议任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_task")
public class MeetingTask extends BaseEntity {

    private Long meetingId;
    private String taskDesc;
    private Long respUserId;
    private String respUserName;
    private LocalDate dueDate;
    /** PENDING/DONE/OVERDUE */
    private String status;
}
