package com.reip.portal.controller;

import com.reip.common.result.Result;
import com.reip.portal.service.CockpitService;
import com.reip.portal.service.CockpitV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 驾驶舱 /api/cockpit
 * 一期：/overview（保留）；二期：三级驾驶舱 + 专题 + 下钻（真实取数）
 */
@RestController
@RequestMapping("/api/cockpit")
@RequiredArgsConstructor
public class CockpitController {

    private final CockpitService svc;
    private final CockpitV2Service svc2;

    /** 一期：经营卡片 + 项目红黄绿灯 + 近6个月趋势（保留，内部已改真实取数） */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(svc.overview());
    }

    /** 集团驾驶舱：六卡 + 全国项目地图(红黄绿) + 区域排名 + 投资兑现度 + 风险预警聚合 */
    @GetMapping("/group")
    public Result<Map<String, Object>> group() {
        return Result.ok(svc2.group());
    }

    /** 区域驾驶舱：项目矩阵 + 三算对比 + 费效 + 招采阳光度 */
    @GetMapping("/region/{orgId}")
    public Result<Map<String, Object>> region(@PathVariable Long orgId) {
        return Result.ok(svc2.region(orgId));
    }

    /** 项目驾驶舱：全周期时间轴 + 供销存瀑布 + 动态利润 + 三算 + 红黄灯 + 三台账 */
    @GetMapping("/project/{projectId}")
    public Result<Map<String, Object>> project(@PathVariable Long projectId) {
        return Result.ok(svc2.project(projectId));
    }

    /** 专题分析：value/cost/sales/proc/expense */
    @GetMapping("/topics/{topic}")
    public Result<Map<String, Object>> topic(@PathVariable String topic) {
        return Result.ok(svc2.topic(topic));
    }

    /** 下钻：指标 → 明细单据穿透（orgId/projectId/month 可选） */
    @GetMapping("/drill/{metricCode}")
    public Result<Map<String, Object>> drill(@PathVariable String metricCode,
                                             @RequestParam(required = false) Long orgId,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String month) {
        return Result.ok(svc2.drill(metricCode, orgId, projectId, month));
    }
}
