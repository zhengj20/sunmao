package com.reip.workflow.flowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.workflow.entity.WflTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 wfl_template.definition(JSON) 转换为 BPMN2.0 XML。
 * 规则：
 *  - start → startEvent
 *  - APPROVE + ANY → userTask（候选人由 WorkflowService 在发起/推进时按 wflBase_<node> 运行时挂接，一人完成即推进）
 *  - APPROVE + ALL → userTask + multiInstance（collection=wflBase_<node>，每个子任务运行时挂接对应审批人，全部完成才推进）
 *  - CONDITION → exclusiveGateway，各 branch 生成 sequenceFlow，带 when 的用 ${field op value}，无 when 的为默认流
 *  - END → endEvent
 *  - 数组顺序即主路径：非 CONDITION/非 END 节点连到数组下一个节点
 */
public final class BpmnConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BpmnConverter() {
    }

    public static String convert(WflTemplate tpl) {
        if (tpl.getBizType() == null || tpl.getBizType().isBlank()) {
            throw new BizException("模板缺少bizType，无法生成BPMN:" + tpl.getTemplateCode());
        }
        JsonNode def;
        try {
            def = MAPPER.readTree(tpl.getDefinition());
        } catch (Exception e) {
            throw new BizException("模板[" + tpl.getTemplateCode() + "]定义JSON解析失败");
        }
        String startId = def.path("start").asText(null);
        if (startId == null || startId.isBlank()) throw new BizException("模板缺少start节点:" + tpl.getTemplateCode());
        List<JsonNode> nodes = new ArrayList<>();
        def.path("nodes").forEach(nodes::add);
        if (nodes.isEmpty()) throw new BizException("模板缺少节点:" + tpl.getTemplateCode());

        // 预收集所有节点 id，校验 start 与各分支目标均存在
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (JsonNode n : nodes) ids.add(n.path("id").asText());
        if (!ids.contains(startId)) throw new BizException("模板start节点不存在:" + startId);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ");
        xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        xml.append("xmlns:flowable=\"http://flowable.org/bpmn\" ");
        xml.append("targetNamespace=\"http://www.reip.com/workflow\">\n");
        xml.append("  <process id=\"").append(esc(tpl.getBizType())).append("\" name=\"")
                .append(esc(tpl.getName() == null ? tpl.getTemplateCode() : tpl.getName())).append("\" isExecutable=\"true\">\n");

        // startEvent（合成的流程入口，序列流 start → 首个业务节点）
        xml.append("    <startEvent id=\"start\" name=\"开始\"/>\n");

        // ---- 节点元素 ----
        boolean hasEnd = false;
        for (JsonNode n : nodes) {
            String id = n.path("id").asText();
            String name = n.path("name").asText(id);
            String type = n.path("type").asText("APPROVE");
            switch (type) {
                case "APPROVE" -> {
                    xml.append("    <userTask id=\"").append(esc(id)).append("\" name=\"").append(esc(name)).append("\">\n");
                    xml.append("      <extensionElements>\n");
                    xml.append("        <flowable:taskListener event=\"create\" expression=\"${wflApproverListener}\"/>\n");
                    xml.append("      </extensionElements>\n");
                    if ("ALL".equals(n.path("countersign").asText("ANY"))) {
                        xml.append("      <multiInstanceLoopCharacteristics isSequential=\"true\" flowable:collection=\"${wflBase_")
                                .append(esc(id)).append("}\" flowable:elementVariable=\"approver\">\n");
                        xml.append("        <completionCondition>${nrOfCompletedInstances &gt;= nrOfInstances}</completionCondition>\n");
                        xml.append("      </multiInstanceLoopCharacteristics>\n");
                    }
                    xml.append("    </userTask>\n");
                }
                case "CONDITION" -> {
                    String defaultFlowId = defaultFlowId(n, id);
                    xml.append("    <exclusiveGateway id=\"").append(esc(id)).append("\" name=\"").append(esc(name)).append("\"");
                    if (defaultFlowId != null) xml.append(" default=\"").append(defaultFlowId).append("\"");
                    xml.append("/>\n");
                }
                case "END" -> {
                    hasEnd = true;
                    xml.append("    <endEvent id=\"").append(esc(id)).append("\" name=\"").append(esc(name)).append("\"/>\n");
                }
                default -> throw new BizException("模板[" + tpl.getTemplateCode() + "]存在未知节点类型:" + type);
            }
        }

        // ---- 序列流 ----
        // start → startId
        xml.append(flow("sf_start", "start", startId, null));
        // CONDITION 分支
        for (JsonNode n : nodes) {
            if (!"CONDITION".equals(n.path("type").asText())) continue;
            String id = n.path("id").asText();
            int bi = 0;
            for (JsonNode b : n.path("branches")) {
                String next = b.path("next").asText(null);
                if (next == null || next.isBlank()) continue;
                if (!ids.contains(next)) throw new BizException("条件节点[" + id + "]分支目标不存在:" + next);
                String flowId = "sf_" + id + "_b" + (bi++);
                JsonNode when = b.get("when");
                String cond = (when != null && when.isObject()) ? buildCondition(when, id) : null;
                xml.append(flow(flowId, id, next, cond));
            }
        }
        // 主路径：非 CONDITION/非 END 节点连到数组下一个节点
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode n = nodes.get(i);
            String type = n.path("type").asText("APPROVE");
            if ("CONDITION".equals(type) || "END".equals(type)) continue;
            String id = n.path("id").asText();
            if (i + 1 < nodes.size()) {
                String nextId = nodes.get(i + 1).path("id").asText();
                if (!ids.contains(nextId)) throw new BizException("节点[" + id + "]的下一节点不存在:" + nextId);
                String flowId = "sf_" + id;
                xml.append(flow(flowId, id, nextId, null));
            }
        }

        // ---- 兜底：确保流程可达终点 ----
        if (!hasEnd) {
            xml.append("    <endEvent id=\"__auto_end\" name=\"结束\"/>\n");
            String lastId = nodes.get(nodes.size() - 1).path("id").asText();
            xml.append(flow("sf_auto_end", lastId, "__auto_end", null));
        }

        xml.append("  </process>\n");
        xml.append("</definitions>\n");
        return xml.toString();
    }

    /** 无 when 的分支即默认流，返回其 flowId；无则 null */
    private static String defaultFlowId(JsonNode condNode, String id) {
        int bi = 0;
        for (JsonNode b : condNode.path("branches")) {
            String next = b.path("next").asText(null);
            if (next == null || next.isBlank()) continue;
            if (b.get("when") == null) return "sf_" + id + "_b" + bi;
            bi++;
        }
        return null;
    }

    private static String buildCondition(JsonNode when, String nodeId) {
        String field = when.path("field").asText("amount");
        String op = when.path("op").asText(">=");
        JsonNode value = when.path("value");
        if (!value.isNumber() && !value.isTextual()) throw new BizException("条件节点[" + nodeId + "]阈值配置错误");
        String val = value.asText();
        String symbol = switch (op) {
            case ">=" -> ">=";
            case ">" -> ">";
            case "<=" -> "<=";
            case "<" -> "<";
            case "==" -> "==";
            case "!=" -> "!=";
            default -> throw new BizException("不支持的条件操作符:" + op);
        };
        return "${" + field + " " + symbol + " " + val + "}";
    }

    private static String flow(String id, String src, String tgt, String condition) {
        StringBuilder s = new StringBuilder();
        s.append("    <sequenceFlow id=\"").append(id).append("\" sourceRef=\"").append(esc(src)).append("\" targetRef=\"").append(esc(tgt)).append("\"");
        if (condition != null) {
            s.append("><conditionExpression xsi:type=\"tFormalExpression\">").append(esc(condition)).append("</conditionExpression></sequenceFlow>");
        } else {
            s.append("/>");
        }
        s.append("\n");
        return s.toString();
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
