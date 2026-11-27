package com.reip.invest.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import com.reip.invest.service.InvestProfitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 事件联动：监听 "cost.dynamic.updated"（成本动态快照更新）
 * payload 含 projectId / dynamicTotal / snapMonth → 自动刷新该项目当月动态利润
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostDynamicUpdatedHandler implements EventHandler {

    public static final String TOPIC = "cost.dynamic.updated";

    private final InvestProfitService profitService;
    private final ObjectMapper objectMapper;

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(DomainEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload() == null ? "{}" : event.getPayload());
            long projectId = payload.path("projectId").asLong(0);
            if (projectId <= 0) {
                log.warn("[event] cost.dynamic.updated 缺少 projectId，忽略 payload={}", event.getPayload());
                return;
            }
            String month = payload.path("snapMonth").asText(null);
            if (month == null || month.isBlank()) month = YearMonth.now().toString();
            profitService.refresh(projectId, month);
            log.info("[event] 动态利润已联动刷新 projectId={} month={}", projectId, month);
        } catch (Exception e) {
            log.error("[event] 动态利润联动刷新失败 bizType={} bizId={}", event.getBizType(), event.getBizId(), e);
        }
    }
}
