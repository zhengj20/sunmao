package com.reip.ai.controller;

import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.service.AiModelConfigService;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型配置（CRUD + 测试连接）
 */
@RestController
@RequestMapping("/api/ai/model-config")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService svc;

    @GetMapping("/page")
    public Result<PageResult<AiModelConfig>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String provider,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, provider, status));
    }

    @GetMapping("/list")
    public Result<List<AiModelConfig>> list() {
        return Result.ok(svc.list());
    }

    @GetMapping("/{id}")
    public Result<AiModelConfig> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody AiModelConfig c) {
        return Result.ok(svc.create(c));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiModelConfig c) {
        svc.update(id, c);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        return Result.ok(svc.test(id));
    }
}
