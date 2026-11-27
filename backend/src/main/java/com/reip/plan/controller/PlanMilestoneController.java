package com.reip.plan.controller;

import com.reip.common.result.Result;
import com.reip.plan.service.PlanMilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 里程碑管理：台账 / 达成率 / 延期原因归类
 */
@RestController
@RequestMapping("/api/plan/milestones")
@RequiredArgsConstructor
public class PlanMilestoneController {

    private final PlanMilestoneService svc;

    /** 里程碑台账（拿地/开工/正负零/预售/开盘/竣备/交付） */
    @GetMapping("/ledger")
    public Result<List<Map<String, Object>>> ledger(@RequestParam Long projectId) {
        return Result.ok(svc.ledger(projectId));
    }

    /** 达成率统计 + 延期原因归类 */
    @GetMapping("/achievement")
    public Result<Map<String, Object>> achievement(@RequestParam Long projectId) {
        return Result.ok(svc.achievement(projectId));
    }
}
