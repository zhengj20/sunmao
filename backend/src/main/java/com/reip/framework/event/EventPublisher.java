package com.reip.framework.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 事件发布器：本地消息表（biz_event Outbox）+ 事务提交后分发
 * 与方案第16章一致：改变金额/数量/状态的写操作，同事务写 Outbox，再投递
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher springPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 发布领域事件。若当前存在事务：同事务写 outbox 留痕，事务提交后同步分发；
     * 无事务时立即分发。
     */
    public void publish(String topic, String bizType, Long bizId, Object payload) {
        DomainEvent event = new DomainEvent();
        event.setTopic(topic);
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setOccurredAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        try {
            event.setPayload(payload == null ? "{}" : objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(event);
                }
            });
        } else {
            dispatch(event);
        }
    }

    private void dispatch(DomainEvent event) {
        springPublisher.publishEvent(event);
    }

    /**
     * 事件分发器：按 topic 路由到所有订阅的 EventHandler
     */
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    public static class Dispatcher {
        private final List<EventHandler> handlers;
        private Map<String, List<EventHandler>> route;

        @org.springframework.context.event.EventListener
        public void on(DomainEvent event) {
            if (route == null) {
                route = handlers.stream().collect(Collectors.groupingBy(EventHandler::topic));
            }
            List<EventHandler> targets = route.get(event.getTopic());
            if (targets == null || targets.isEmpty()) {
                log.debug("[event] 无订阅者 topic={}", event.getTopic());
                return;
            }
            for (EventHandler h : targets) {
                try {
                    h.handle(event);
                } catch (Exception e) {
                    log.error("[event] 处理失败 topic={} handler={}", event.getTopic(),
                            h.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
