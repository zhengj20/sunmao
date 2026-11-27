package com.reip.workflow.api;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 工作流引擎对外契约（各业务模块通过此接口接入审批流）
 * 实现见 com.reip.workflow.service（WorkflowClientImpl）
 *
 * bizType 取值（与 wfl_template.biz_type 一致）：
 * COST_TARGET / COST_PLAN / COST_CONTRACT / COST_CHANGE / COST_PAYMENT / COST_INVALID /
 * PROC_REQUIREMENT / PROC_PROJECT / PROC_ENTRY / PROC_AWARD / PROC_DIRECT
 */
public interface WorkflowClient {

    /**
     * 发起审批：创建流程实例并生成首个节点任务。
     * amount 为提交时点金额快照（条件路由用）；snapshot 为单据摘要（待办展示）。
     * 若该单据已有在途流程实例，抛 BizException。
     */
    void start(String bizType, Long bizId, String title, BigDecimal amount, Map<String, Object> snapshot);

    /** 发起人撤回（仅 RUNNING 状态可撤回，撤回后实例结束、剩余任务作废） */
    void withdraw(String bizType, Long bizId);

    /** 当前用户待办任务数 */
    long todoCount();
}
