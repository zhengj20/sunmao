package com.reip.workflow.flowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发起时解析某审批节点的基础审批人：
 *  - assigneeType=ROLE：角色码 → 该角色所有启用用户
 *  - assigneeType=USER：assignees 即用户ID
 * 结果写入流程变量 wflBase_<节点id>，供或签挂候选 / 会签多实例 / 台账使用。
 * （委托代理在运行时由 WorkflowService.prepareActiveTasks 按审批人逐个挂接，不在此处展开。）
 */
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final WorkflowMappers.UserLookupMapper userLookupMapper;

    /** 基础审批人（角色成员或指定用户），不含代理 */
    public List<Long> baseUsers(JsonNode node, String bizType) {
        Set<Long> users = new LinkedHashSet<>();
        boolean userMode = "USER".equals(node.path("assigneeType").asText("ROLE"));
        for (JsonNode a : node.path("assignees")) {
            String raw = a.asText();
            if (raw == null || raw.isBlank()) continue;
            if (userMode) {
                try {
                    users.add(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                }
            } else {
                for (Map<String, Object> u : userLookupMapper.selectUsersByRoleCode(raw)) {
                    users.add(((Number) u.get("id")).longValue());
                }
            }
        }
        return new ArrayList<>(users);
    }
}
