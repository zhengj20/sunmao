package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.service.ProcLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 招采台账
 */
@RestController
@RequestMapping("/api/proc/ledger")
@RequiredArgsConstructor
public class ProcLedgerController {

    private final ProcLedgerService svc;

    /** 全流程台账（立项维度 JOIN 定标） */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "20") long size,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, kw));
    }

    /** 招采过程总览：9 节点完成标记（地铁图看板） */
    @GetMapping("/overview")
    public Result<PageResult<Map<String, Object>>> overview(@RequestParam(defaultValue = "1") long current,
                                                            @RequestParam(defaultValue = "20") long size,
                                                            @RequestParam(required = false) Long projectId,
                                                            @RequestParam(required = false) String method,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String kw) {
        return Result.ok(svc.overview(current, size, projectId, method, status, kw));
    }

    /** 采购统计：周期/偏差率/公开招标占比/直接委托占比 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam Long projectId) {
        return Result.ok(svc.stats(projectId));
    }
}
