package com.reip.portal.controller;

import com.reip.common.result.Result;
import com.reip.portal.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 门户工作台 /api/portal
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService svc;

    /** 工作台聚合：待办/公告/经营指标/我的草稿/最新事件 */
    @GetMapping("/workbench")
    public Result<Map<String, Object>> workbench() {
        return Result.ok(svc.workbench());
    }
}
