package com.reip.cost.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.cost.service.*;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流结束事件回调：payload JSON 含 bizType/bizId/approved
 * approved=true → 单据置通过态并执行通过后动作；false → 驳回退回可编辑状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostWorkflowEventHandler implements EventHandler {

    public static final String TOPIC = "workflow.instance.finished";

    private final ObjectMapper objectMapper;
    private final CostTargetService targetService;
    private final CostPlanService planService;
    private final CostContractService contractService;
    private final CostChangeService changeService;
    private final CostPaymentService paymentService;
    private final CostInvalidService invalidService;
    private final CostSupplementService supplementService;

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
            log.warn("[cost] 工作流回调 payload 解析失败：{}", event.getPayload());
            return;
        }
        String bizType = text(node, "bizType");
        Long bizId = longVal(node, "bizId");
        if (bizType == null || bizId == null) return;
        boolean approved = node.path("approved").asBoolean(false);
        switch (bizType) {
            case "COST_TARGET" -> {
                if (approved) targetService.onWorkflowApproved(bizId);
                else targetService.onWorkflowRejected(bizId);
            }
            case "COST_PLAN" -> {
                if (approved) planService.onWorkflowApproved(bizId);
                else planService.onWorkflowRejected(bizId);
            }
            case "COST_CONTRACT" -> {
                if (approved) contractService.onWorkflowApproved(bizId);
                else contractService.onWorkflowRejected(bizId);
            }
            case "COST_CHANGE" -> {
                if (approved) changeService.onWorkflowApproved(bizId);
                else changeService.onWorkflowRejected(bizId);
            }
            case "COST_PAYMENT" -> {
                if (approved) paymentService.onWorkflowApproved(bizId);
                else paymentService.onWorkflowRejected(bizId);
            }
            case "COST_INVALID" -> {
                if (approved) invalidService.onWorkflowApproved(bizId);
                else invalidService.onWorkflowRejected(bizId);
            }
            case "COST_SUPPLEMENT" -> {
                if (approved) supplementService.onWorkflowApproved(bizId);
                else supplementService.onWorkflowRejected(bizId);
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
