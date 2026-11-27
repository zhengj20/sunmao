package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.ai.entity.AiChatMessage;
import com.reip.ai.entity.AiChatSession;
import com.reip.ai.entity.AiKbDocument;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.digital.service.DigitalExecutor;
import com.reip.framework.auth.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 会话：复用 AiQueryService 的规则+LLM 能力，落历史。R2：会话按当前用户隔离。
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiMappers.ChatSessionMapper sessionMapper;
    private final AiMappers.ChatMessageMapper messageMapper;
    private final AiQueryService queryService;
    private final AiKbService kbService;
    private final AiLlmGateway llmGateway;
    private final DigitalExecutor digitalExecutor;
    private final ObjectMapper objectMapper;

    public AiChatSession createSession(String title) {
        AiChatSession s = new AiChatSession();
        s.setSessionId("S-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        s.setUserId(UserContext.require().getUserId());
        s.setTitle(title == null || title.isBlank() ? "新会话" : title);
        sessionMapper.insert(s);
        return s;
    }

    public List<AiChatSession> mySessions() {
        Long uid = UserContext.require().getUserId();
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, uid).orderByDesc(AiChatSession::getId));
    }

    public List<AiChatMessage> messages(String sessionId) {
        requireOwn(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId).orderByAsc(AiChatMessage::getId));
    }

    public Map<String, Object> send(String sessionId, String question) {
        requireOwn(sessionId);
        if (question == null || question.isBlank()) throw new BizException("请输入内容");

        // 用户消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        // 复用指标问答（规则+LLM）
        Map<String, Object> answer = queryService.ask(question, null);

        // 助手消息
        AiChatMessage asstMsg = new AiChatMessage();
        asstMsg.setSessionId(sessionId);
        asstMsg.setRole("ASSISTANT");
        asstMsg.setContent(String.valueOf(answer.get("answer")));
        asstMsg.setAiMode(String.valueOf(answer.get("aiMode")));
        asstMsg.setRefs(safeJson(Map.of(
                "metricCode", answer.get("metricCode") == null ? "" : answer.get("metricCode"),
                "matched", answer.get("matched"),
                "value", answer.get("value") == null ? "" : answer.get("value"),
                "source", answer.get("source") == null ? "" : answer.get("source"))));
        messageMapper.insert(asstMsg);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("userMessageId", userMsg.getId());
        out.put("assistantMessageId", asstMsg.getId());
        out.put("answer", answer);
        return out;
    }

    public Map<String, Object> sendMulti(String sessionId, String question, String mode, Long employeeId, Long taskId) {
        requireOwn(sessionId);
        if (question == null || question.isBlank()) throw new BizException("请输入内容");

        String effectiveMode = (mode == null || mode.isBlank()) ? "METRIC" : mode.toUpperCase();

        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        Map<String, Object> answer;

        if ("METRIC".equals(effectiveMode)) {
            answer = queryService.ask(question, null);
        } else if ("KB".equals(effectiveMode)) {
            List<AiKbDocument> docs = kbService.searchForContext(question, null, 5);
            List<Map<String, Object>> docList = new ArrayList<>();
            for (AiKbDocument d : docs) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("docCode", d.getDocCode());
                dm.put("title", d.getTitle());
                dm.put("category", d.getCategory());
                String c = d.getContent() == null ? "" : d.getContent();
                dm.put("snippet", c.length() > 300 ? c.substring(0, 300) + "…" : c);
                docList.add(dm);
            }

            String text;
            String aiMode;
            if (docList.isEmpty()) {
                text = "未找到相关文档，无法回答该问题。";
                aiMode = "KB";
            } else {
                StringBuilder sys = new StringBuilder();
                sys.append("你是企业知识库助手。请根据以下相关文档内容回答用户问题。\n");
                sys.append("要求：回答要准确、简洁、有条理，仅基于文档内容作答，不要编造文档中没有的信息。\n\n");
                sys.append("【参考文档】\n");
                for (AiKbDocument d : docs) {
                    sys.append("### ").append(d.getTitle()).append("（").append(d.getCategory() == null ? "未分类" : d.getCategory()).append("）\n");
                    String c = d.getContent() == null ? "" : d.getContent();
                    sys.append(c.length() > 800 ? c.substring(0, 800) + "…" : c).append("\n\n");
                }
                AiLlmResult res = llmGateway.chatWith(null, sys.toString(), question);
                if (res.isSuccess()) {
                    text = res.getContent();
                    aiMode = "KB-LLM";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("找到 ").append(docList.size()).append(" 篇相关文档：\n");
                    for (Map<String, Object> dm : docList) {
                        sb.append("- ").append(dm.get("title")).append("：").append(dm.get("snippet")).append("\n");
                    }
                    text = sb.toString().trim();
                    aiMode = "KB";
                }
            }
            answer = new LinkedHashMap<>();
            answer.put("answer", text);
            answer.put("aiMode", aiMode);
            answer.put("docs", docList);
        } else if ("EMPLOYEE".equals(effectiveMode)) {
            if (taskId == null) throw new BizException("EMPLOYEE 模式需要指定 taskId");
            Long runId = digitalExecutor.execute(taskId, "MANUAL");
            answer = new LinkedHashMap<>();
            answer.put("answer", "已触发执行，runId=" + runId);
            answer.put("aiMode", "EMPLOYEE");
            answer.put("runId", runId);
        } else {
            throw new BizException("不支持的模式：" + mode + "，可选 METRIC / KB / EMPLOYEE");
        }

        AiChatMessage asstMsg = new AiChatMessage();
        asstMsg.setSessionId(sessionId);
        asstMsg.setRole("ASSISTANT");
        asstMsg.setContent(String.valueOf(answer.get("answer")));
        asstMsg.setAiMode(String.valueOf(answer.get("aiMode")));
        asstMsg.setRefs(safeJson(answer));
        messageMapper.insert(asstMsg);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("userMessageId", userMsg.getId());
        out.put("assistantMessageId", asstMsg.getId());
        out.put("answer", answer);
        return out;
    }

    public void deleteSession(String sessionId) {
        requireOwn(sessionId);
        sessionMapper.delete(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId, sessionId));
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId, sessionId));
    }

    private AiChatSession requireOwn(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new BizException("缺少会话ID");
        AiChatSession s = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId).last("LIMIT 1"));
        if (s == null) throw new BizException("会话不存在");
        Long uid = UserContext.require().getUserId();
        if (!uid.equals(s.getUserId())) throw new BizException("无权访问该会话");
        return s;
    }

    private String safeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
