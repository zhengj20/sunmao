package com.reip.ai.controller;

import com.reip.ai.entity.AiChatMessage;
import com.reip.ai.entity.AiChatSession;
import com.reip.ai.service.AiChatService;
import com.reip.common.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 会话
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService svc;

    @Data
    public static class SessionForm {
        private String title;
    }

    @Data
    public static class SendForm {
        private String question;
        private String mode;
        private Long employeeId;
        private Long taskId;
    }

    @PostMapping("/sessions")
    public Result<AiChatSession> create(@RequestBody(required = false) SessionForm f) {
        return Result.ok(svc.createSession(f == null ? null : f.getTitle()));
    }

    @GetMapping("/sessions")
    public Result<List<AiChatSession>> sessions() {
        return Result.ok(svc.mySessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AiChatMessage>> messages(@PathVariable String sessionId) {
        return Result.ok(svc.messages(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/send")
    public Result<Map<String, Object>> send(@PathVariable String sessionId, @RequestBody SendForm f) {
        if (f == null) {
            return Result.ok(svc.send(sessionId, null));
        }
        return Result.ok(svc.sendMulti(sessionId, f.getQuestion(), f.getMode(), f.getEmployeeId(), f.getTaskId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        svc.deleteSession(sessionId);
        return Result.ok();
    }
}
