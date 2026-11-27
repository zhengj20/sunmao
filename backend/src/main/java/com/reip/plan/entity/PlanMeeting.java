package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 计划会议
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_meeting")
public class PlanMeeting extends BaseEntity {

    private String meetingNo;
    private Long projectId;
    /** REGULAR例会/SPECIAL专题 */
    private String meetingType;
    private LocalDate meetingDate;
    private String agenda;
    private String minutes;
    /** DONE/CANCELLED */
    private String status;

    /** 展示用：决议任务（非持久化） */
    private transient List<MeetingTask> tasks;
}
