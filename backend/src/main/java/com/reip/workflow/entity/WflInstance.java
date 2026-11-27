package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流程实例（同一单据同刻仅一在途实例，唯一约束+状态机保证）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wfl_instance")
public class WflInstance extends BaseEntity {
    private Long templateId;
    private String templateCode;
    private String bizType;
    private Long bizId;
    /** 单据标题（待办展示） */
    private String bizTitle;
    /** 单据金额（条件路由用，提交时点快照） */
    private BigDecimal bizAmount;
    /** 业务快照 JSON（摘要展示） */
    private String snapshot;
    private String currentNodeId;
    /** Flowable 流程实例ID（业务实例 ↔ 引擎实例映射） */
    private String processInstanceId;
    /** 在途标记：1在途，结束置NULL（NULL不参与uk_biz_inflight唯一约束，同单多个已结束实例不冲突） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer inflightFlag;
    /** RUNNING/APPROVED/REJECTED/WITHDRAWN */
    private String status;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
}
