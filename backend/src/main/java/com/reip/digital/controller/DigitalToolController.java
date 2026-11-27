package com.reip.digital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.result.Result;
import com.reip.digital.entity.DigTool;
import com.reip.digital.service.DigitalToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数字员工 · 工具（HTTP 工具 + 内置工具，技能可绑定）
 */
@RestController
@RequestMapping("/api/digital/tools")
@RequiredArgsConstructor
public class DigitalToolController {

    private final DigitalToolService svc;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<List<DigTool>> list() {
        return Result.ok(svc.list());
    }

    @PostMapping
    public Result<DigTool> save(@RequestBody DigTool t) {
        return Result.ok(svc.save(t));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 测试调用：{ "toolId": 1, "args": {"calc": "(1+2)*3"} } */
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody Map<String, Object> p) {
        try {
            Long toolId = Long.valueOf(String.valueOf(p.get("toolId")));
            String argsJson = p.get("args") == null ? null : objectMapper.writeValueAsString(p.get("args"));
            String out = svc.invoke(toolId, argsJson);
            return Result.ok(Map.of("ok", true, "output", out));
        } catch (Exception e) {
            return Result.ok(Map.of("ok", false, "message", e.getMessage() == null ? "调用失败" : e.getMessage()));
        }
    }
}
