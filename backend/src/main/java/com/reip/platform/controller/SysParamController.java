package com.reip.platform.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.platform.entity.SysOpLog;
import com.reip.platform.service.SysMgmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys")
@RequiredArgsConstructor
public class SysParamController {

    private final SysMgmtService svc;

    @GetMapping("/params")
    public Result<List<Map<String, String>>> params() {
        return Result.ok(svc.paramList());
    }

    @PutMapping("/params")
    public Result<Void> saveParam(@RequestBody Map<String, String> body) {
        svc.saveParam(body.get("key"), body.get("value"));
        return Result.ok();
    }

    @GetMapping("/oplogs/page")
    public Result<PageResult<SysOpLog>> opLogs(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String module,
                                               @RequestParam(required = false) Long userId) {
        return Result.ok(svc.opLogPage(current, size, module, userId));
    }
}
