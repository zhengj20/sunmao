package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmQualityIssue;
import com.reip.mdm.service.MdmQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 主数据数据质量巡检 /api/mdm/quality
 */
@RestController
@RequestMapping("/api/mdm/quality")
@RequiredArgsConstructor
public class MdmQualityController {

    private final MdmQualityService svc;

    /** 执行巡检并落库（重复扫描自动去重） */
    @PostMapping("/scan")
    public Result<Map<String, Object>> scan() {
        return Result.ok(svc.scan());
    }

    /** 问题分页（severity/status/entityType 过滤） */
    @GetMapping("/issues")
    public Result<PageResult<MdmQualityIssue>> issues(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) String severity,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String entityType) {
        return Result.ok(svc.page(current, size, severity, status, entityType));
    }

    /** 处置问题：{status:"FIXED"|"IGNORED"} */
    @PutMapping("/issues/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        svc.resolve(id, body.get("status"));
        return Result.ok();
    }

    /** 汇总报告：severity/entityType 计数 + Top 问题 */
    @GetMapping("/report")
    public Result<Map<String, Object>> report() {
        return Result.ok(svc.report());
    }
}
