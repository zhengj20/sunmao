package com.reip.integration.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.integration.entity.IntAppKey;
import com.reip.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 调用方密钥 /api/int/app-keys
 */
@RestController
@RequestMapping("/api/int/app-keys")
@RequiredArgsConstructor
public class AppKeyController {

    private final IntegrationService svc;

    @GetMapping("/page")
    public Result<PageResult<IntAppKey>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String appName,
                                              @RequestParam(required = false) String status) {
        return Result.ok(svc.appKeyPage(current, size, appName, status));
    }

    @GetMapping("/{id}")
    public Result<IntAppKey> detail(@PathVariable Long id) {
        return Result.ok(svc.appKeyDetail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IntAppKey appKey) {
        return Result.ok(svc.createAppKey(appKey));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntAppKey appKey) {
        appKey.setId(id);
        svc.updateAppKey(appKey);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.deleteAppKey(id);
        return Result.ok();
    }
}
