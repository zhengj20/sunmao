package com.reip.integration.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.integration.entity.IntAdapterConfig;
import com.reip.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 集成适配器 /api/int/adapters
 */
@RestController
@RequestMapping("/api/int/adapters")
@RequiredArgsConstructor
public class AdapterController {

    private final IntegrationService svc;

    @GetMapping("/page")
    public Result<PageResult<IntAdapterConfig>> page(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String adapterCode,
                                                      @RequestParam(required = false) String adapterType,
                                                      @RequestParam(required = false) String status) {
        return Result.ok(svc.adapterPage(current, size, adapterCode, adapterType, status));
    }

    @GetMapping("/{id}")
    public Result<IntAdapterConfig> detail(@PathVariable Long id) {
        return Result.ok(svc.adapterDetail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IntAdapterConfig adapter) {
        return Result.ok(svc.createAdapter(adapter));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntAdapterConfig adapter) {
        adapter.setId(id);
        svc.updateAdapter(adapter);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.deleteAdapter(id);
        return Result.ok();
    }

    /** 测试连接（模拟） */
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        return Result.ok(svc.testAdapter(id));
    }
}
