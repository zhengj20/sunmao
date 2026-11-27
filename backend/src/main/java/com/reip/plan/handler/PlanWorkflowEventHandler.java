package com.reip.plan.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import com.reip.plan.service.PlanAdjustService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计划模块工作流结果处理：监听 workflow.instance.finished
 *   PLAN_ADJUST  计划调整审批 → 通过 APPROVED(更新任务时间+刷新基线) / 驳回 REJECTED(回草稿)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanWorkflowEventHandler implements EventHandler {

    public static final String TOPIC = "workflow.instance.finished";

    private final ObjectMapper objectMapper;
    private final PlanAdjustService adjustService;

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(DomainEvent event) {
        JsonNode node;
        try {
            node = objectMapper.readTree(event.getPayload() == null ? "{}" : event.getPayload());
        } catch (Exception e) {
            log.warn("[plan] 工作流回调 payload 解析失败：{}", event.getPayload());
            return;
        }
        String bizType = text(node, "bizType");
        Long bizId = longVal(node, "bizId");
        if (bizType == null || bizId == null) return;
        boolean approved = node.path("approved").asBoolean(false);
        switch (bizType) {
            case "PLAN_ADJUST" -> {
                if (approved) adjustService.onApproved(bizId);
                else adjustService.onRejected(bizId);
            }
            default -> { /* 非本模块单据，忽略 */ }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }
}
