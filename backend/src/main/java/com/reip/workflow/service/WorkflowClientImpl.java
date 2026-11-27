package com.reip.workflow.service;

import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 工作流引擎对外契约实现：各业务模块通过 WorkflowClient 接入审批流。
 * 底座已换为 Flowable，对外签名与语义保持不变（发起/撤回/待办数 + 结束事件）。
 */
@Service
@RequiredArgsConstructor
public class WorkflowClientImpl implements WorkflowClient {

    private final WorkflowService workflowService;

    @Override
    @Transactional
    public void start(String bizType, Long bizId, String title, BigDecimal amount, Map<String, Object> snapshot) {
        workflowService.startFlow(bizType, bizId, title, amount, snapshot);
    }

    @Override
    @Transactional
    public void withdraw(String bizType, Long bizId) {
        workflowService.withdrawFlow(bizType, bizId);
    }

    @Override
    public long todoCount() {
        return workflowService.todoCount();
    }
}
