package com.reip.framework.event;

/**
 * 领域事件处理器：各模块实现并注册为 Spring Bean，自动被发现
 */
public interface EventHandler {
    /** 订阅的主题（topic） */
    String topic();

    /** 处理事件（事务提交后执行；处理失败仅记录日志，不影响主流程） */
    void handle(DomainEvent event);
}
