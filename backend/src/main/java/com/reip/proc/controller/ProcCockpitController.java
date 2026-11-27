package com.reip.proc.controller;

import com.reip.common.result.Result;
import com.reip.proc.service.ProcCockpitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 招采驾驶舱 /api/proc/cockpit
 */
@RestController
@RequestMapping("/api/proc/cockpit")
@RequiredArgsConstructor
public class ProcCockpitController {

    private final ProcCockpitService svc;

    /** 指标总览 + 统计分析 + 预警 */
    @GetMapping
    public Result<Map<String, Object>> cockpit() {
        return Result.ok(svc.cockpit());
    }
}
