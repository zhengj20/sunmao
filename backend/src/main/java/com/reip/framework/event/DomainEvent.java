package com.reip.framework.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 领域事件（对齐方案第2章：Topic 命名 {domain}.{object}.{action}）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent {
    /** 事件主题，如 procurement.award.confirmed */
    private String topic;
    /** 业务类型（单据类型） */
    private String bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 事件负载 JSON */
    private String payload;
    private String occurredAt;
}
