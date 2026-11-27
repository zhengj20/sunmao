package com.reip.integration.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.integration.entity.IntCallLog;
import com.reip.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 集成调用日志 /api/int/logs
 */
@RestController
@RequestMapping("/api/int/logs")
@RequiredArgsConstructor
public class CallLogController {

    private final IntegrationService svc;

    /** 分页查询（按适配器/成功标记过滤） */
    @GetMapping("/page")
    public Result<PageResult<IntCallLog>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String adapterCode,
                                               @RequestParam(required = false) Integer success) {
        return Result.ok(svc.logPage(current, size, adapterCode, success));
    }

    /** 按适配器统计成功数/失败数/平均耗时 */
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        return Result.ok(svc.logStats());
    }
}
