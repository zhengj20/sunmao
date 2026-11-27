package com.reip.integration.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.integration.entity.IntApiRegistry;
import com.reip.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 开放API注册 /api/int/apis
 */
@RestController
@RequestMapping("/api/int/apis")
@RequiredArgsConstructor
public class ApiRegistryController {

    private final IntegrationService svc;

    @GetMapping("/page")
    public Result<PageResult<IntApiRegistry>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String apiCode,
                                                    @RequestParam(required = false) String status) {
        return Result.ok(svc.apiPage(current, size, apiCode, status));
    }

    @GetMapping("/{id}")
    public Result<IntApiRegistry> detail(@PathVariable Long id) {
        return Result.ok(svc.apiDetail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IntApiRegistry api) {
        return Result.ok(svc.createApi(api));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntApiRegistry api) {
        api.setId(id);
        svc.updateApi(api);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.deleteApi(id);
        return Result.ok();
    }
}
