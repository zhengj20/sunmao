package com.reip.sales.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import com.reip.sales.service.SaleContractService;
import com.reip.sales.service.SaleRefundService;
import com.reip.sales.service.SaleSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 销售模块工作流结果处理：监听 workflow.instance.finished
 * 按 bizType 回写：
 *  - SALE_SUBSCRIPTION            认购审批 → 通过 ACTIVE+房源认购 / 驳回 DRAFT+释放
 *  - SALE_SUBSCRIPTION_TERMINATE  退定审批 → 通过 TERMINATED+释放 / 驳回
 *  - SALE_CONTRACT                签约审批 → 通过 ACTIVE+房源签约+应收+事件 / 驳回 DRAFT
 *  - SALE_REFUND                  退房审批 → 通过 REFUNDED+释放+事件 / 驳回
 *  - SALE_SPECIAL_PRICE           特价审批 → 终态由 wfl_instance 承载（供 R3 校验），此处无需回写
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesWorkflowEventHandler implements EventHandler {

    public static final String TOPIC = "workflow.instance.finished";

    private final ObjectMapper objectMapper;
    private final SaleSubscriptionService subscriptionService;
    private final SaleContractService contractService;
    private final SaleRefundService refundService;

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
            log.warn("[sales] 工作流回调 payload 解析失败：{}", event.getPayload());
            return;
        }
        String bizType = text(node, "bizType");
        Long bizId = longVal(node, "bizId");
        if (bizType == null || bizId == null) return;
        boolean approved = node.path("approved").asBoolean(false);
        switch (bizType) {
            case "SALE_SUBSCRIPTION" -> {
                if (approved) subscriptionService.onApproved(bizId);
                else subscriptionService.onRejected(bizId);
            }
            case "SALE_SUBSCRIPTION_TERMINATE" -> {
                if (approved) refundService.onApproved(bizId);
                else refundService.onRejected(bizId);
            }
            case "SALE_CONTRACT" -> {
                if (approved) contractService.onApproved(bizId);
                else contractService.onRejected(bizId);
            }
            case "SALE_REFUND" -> {
                if (approved) refundService.onApproved(bizId);
                else refundService.onRejected(bizId);
            }
            case "SALE_SPECIAL_PRICE" -> log.info("[sales] 特价房审批结束 approved={} bizId={}（终态由 wfl_instance 承载）", approved, bizId);
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
