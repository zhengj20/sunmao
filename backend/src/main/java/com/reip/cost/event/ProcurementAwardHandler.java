package com.reip.cost.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.cost.service.CostContractService;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 招采定标确认事件：自动创建合同草稿（source=AWARD）
 * payload 含 procProjectId/projectId/supplierId/supplierName/amount/planItemId/title
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcurementAwardHandler implements EventHandler {

    public static final String TOPIC = "procurement.award.confirmed";

    private final ObjectMapper objectMapper;
    private final CostContractService contractService;

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
            log.warn("[cost] 定标事件 payload 解析失败：{}", event.getPayload());
            return;
        }
        Long projectId = longVal(node, "projectId");
        if (projectId == null) return;
        Long contractId = contractService.createFromAward(
                projectId,
                longVal(node, "awardId"),
                longVal(node, "winnerSupplierId"),
                text(node, "winnerSupplierName"),
                decimal(node, "amount"),
                longVal(node, "planItemId"),
                text(node, "title"));
        log.info("[cost] 定标 {} 生成合同草稿 contractId={}", longVal(node, "awardId"), contractId);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private static java.math.BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.decimalValue();
        try {
            return new java.math.BigDecimal(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
