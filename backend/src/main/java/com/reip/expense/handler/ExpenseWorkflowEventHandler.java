package com.reip.expense.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.expense.service.ExpApplyService;
import com.reip.expense.service.ExpLoanService;
import com.reip.expense.service.ExpMatterService;
import com.reip.expense.service.ExpReimburseService;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 费用模块工作流结果处理：监听 workflow.instance.finished，按 bizType 回写终态
 *  - EXP_BUDGET         费用申请审批 → 通过 ACTIVE(保持占用) / 驳回 REJECTED(释放占用)
 *  - EXP_LOAN           借款审批     → 通过 ACTIVE / 驳回 作废(未冲销清零)
 *  - EXP_REIMB_OVERRUN  报销超标特批 → 通过 APPROVED(核销+冲借款+事件) / 驳回 REJECTED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseWorkflowEventHandler implements EventHandler {

    public static final String TOPIC = "workflow.instance.finished";

    private final ObjectMapper objectMapper;
    private final ExpApplyService applyService;
    private final ExpLoanService loanService;
    private final ExpReimburseService reimburseService;
    private final ExpMatterService matterService;

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
            log.warn("[expense] 工作流回调 payload 解析失败：{}", event.getPayload());
            return;
        }
        String bizType = text(node, "bizType");
        Long bizId = longVal(node, "bizId");
        if (bizType == null || bizId == null) return;
        boolean approved = node.path("approved").asBoolean(false);
        switch (bizType) {
            case "EXP_BUDGET" -> {
                if (approved) applyService.onApproved(bizId);
                else applyService.onRejected(bizId);
            }
            case "EXP_LOAN" -> {
                if (approved) loanService.onApproved(bizId);
                else loanService.onRejected(bizId);
            }
            case "EXP_REIMB_OVERRUN" -> {
                if (approved) reimburseService.onApproved(bizId);
                else reimburseService.onRejected(bizId);
            }
            case "EXP_MATTER" -> {
                if (approved) matterService.onApproved(bizId);
                else matterService.onRejected(bizId);
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
