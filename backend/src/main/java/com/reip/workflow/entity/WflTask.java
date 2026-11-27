package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wfl_task")
public class WflTask extends BaseEntity {
    private Long instanceId;
    private String nodeId;
    private String nodeName;
    private Long assigneeId;
    private String assigneeName;
    /** Flowable 任务ID（不落库，仅详情返回） */
    @TableField(exist = false)
    private String flowableTaskId;
    /** 受托代理人 */
    private Long agentId;
    private String agentName;
    /** ANY或签/ALL会签 */
    private String countersign;
    /** PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELLED */
    private String status;
    private String comment;
    /** JSON 附件 */
    private String attachments;
    private LocalDateTime arrivedAt;
    private LocalDateTime actedAt;
    private Long durationMinutes;

    /** 字段级权限（实例详情返回，取自模板节点定义 fieldPerm，不落库）：[{field,visible,editable}] */
    @TableField(exist = false)
    private List<Map<String, Object>> fieldPerm;
}
