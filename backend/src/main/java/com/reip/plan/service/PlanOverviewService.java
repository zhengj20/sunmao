package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.plan.entity.PlanImageProgress;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划总览（一次聚合，供总览页/项目驾驶舱复用）：
 *   KPI 卡片 / 地铁图(里程碑台账) / 专项计划进度 / 施工进度(已确认形象进度) / 指标统计与预警
 * 全部为现有表只读聚合（plan_task / plan_image_progress / plan_adjust），无新表。
 */
@Service
@RequiredArgsConstructor
public class PlanOverviewService {

    private static final List<String> SPECIALTIES = List.of("设计", "报建", "工程", "营销", "交付");

    private final PlanMappers.TaskMapper taskMapper;
    private final PlanMilestoneService milestoneService;
    private final PlanAlertService alertService;
    private final PlanReportService reportService;
    private final PlanImageProgressService imageService;

    public Map<String, Object> overview(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        LocalDate today = LocalDate.now();
        List<PlanTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId).orderByAsc(PlanTask::getId));
        List<Map<String, Object>> subway = milestoneService.ledger(projectId);
        Map<String, Object> ach = milestoneService.achievement(projectId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kpi", kpi(tasks, today, ach, subway));
        r.put("subway", subway);
        r.put("specialty", specialtyRows(tasks));
        r.put("construction", construction(projectId));
        r.put("alert", alert(projectId, tasks, ach));
        return r;
    }

    // ==================== KPI 卡片 ====================

    private Map<String, Object> kpi(List<PlanTask> tasks, LocalDate today,
                                    Map<String, Object> ach, List<Map<String, Object>> subway) {
        long total = tasks.size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskTotal", total);
        m.put("completedCount", tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count());
        m.put("inProgressCount", tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count());
        m.put("redCount", tasks.stream().filter(t -> "RED".equals(t.getAlertStatus())).count());
        m.put("yellowCount", tasks.stream().filter(t -> "YELLOW".equals(t.getAlertStatus())).count());
        m.put("overdueCount", tasks.stream().filter(t -> PlanSupport.overdueDays(t, today) > 0).count());
        m.put("overallProgress", total == 0 ? BigDecimal.ZERO
                : tasks.stream().map(t -> PlanSupport.nvl(t.getProgressPct())).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        m.put("milestoneTotal", ach.get("total"));
        m.put("milestoneCompleted", ach.get("completed"));
        m.put("milestoneDelayed", ach.get("delayed"));
        m.put("achievementRate", ach.get("achievementRate"));
        m.put("currentPhase", currentPhase(tasks));
        m.put("nextMilestone", nextMilestone(today, subway));
        return m;
    }

    /** 当前阶段：第一个进行中的一级任务 */
    private String currentPhase(List<PlanTask> tasks) {
        for (PlanTask t : tasks) {
            if ("一级".equals(t.getLevel()) && "IN_PROGRESS".equals(t.getStatus())) return t.getTaskName();
        }
        return null;
    }

    /** 下一里程碑：台账中第一个未完成的（含剩余/已超天数） */
    private Map<String, Object> nextMilestone(LocalDate today, List<Map<String, Object>> subway) {
        for (Map<String, Object> row : subway) {
            if ("COMPLETED".equals(row.get("status"))) continue;
            LocalDate end = toDate(row.get("planEnd"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskName", row.get("taskName"));
            m.put("planEnd", row.get("planEnd"));
            m.put("remainDays", end == null ? null : ChronoUnit.DAYS.between(today, end));
            m.put("alertStatus", row.get("alertStatus"));
            return m;
        }
        return null;
    }

    // ==================== 专项计划进度 ====================

    private List<Map<String, Object>> specialtyRows(List<PlanTask> tasks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String sp : SPECIALTIES) {
            List<PlanTask> list = new ArrayList<>();
            for (PlanTask t : tasks) if (sp.equals(t.getSpecialty())) list.add(t);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("specialty", sp);
            m.put("taskCount", list.size());
            m.put("avgProgress", list.isEmpty() ? BigDecimal.ZERO
                    : list.stream().map(t -> PlanSupport.nvl(t.getProgressPct())).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP));
            m.put("green", list.stream().filter(t -> "GREEN".equals(t.getAlertStatus())).count());
            m.put("yellow", list.stream().filter(t -> "YELLOW".equals(t.getAlertStatus())).count());
            m.put("red", list.stream().filter(t -> "RED".equals(t.getAlertStatus())).count());
            m.put("currentNode", list.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus()))
                    .map(PlanTask::getTaskName).findFirst().orElse(null));
            out.add(m);
        }
        return out;
    }

    // ==================== 施工进度（已确认形象进度） ====================

    private List<Map<String, Object>> construction(Long projectId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanImageProgress i : imageService.confirmedList(projectId, null)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("building", i.getBuilding());
            m.put("partName", i.getPartName());
            m.put("confirmedPct", i.getConfirmedPct());
            m.put("confirmDate", i.getConfirmDate());
            out.add(m);
        }
        return out;
    }

    // ==================== 指标统计与预警 ====================

    private Map<String, Object> alert(Long projectId, List<PlanTask> tasks, Map<String, Object> ach) {
        Map<String, Object> sup = alertService.supervision(projectId);
        List<Map<String, Object>> dist = new ArrayList<>();
        for (String c : List.of("GREEN", "YELLOW", "RED")) {
            long cnt = tasks.stream().filter(t -> c.equals(t.getAlertStatus())).count();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("alertStatus", c);
            d.put("cnt", cnt);
            dist.add(d);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("distribution", dist);
        m.put("red", sup.get("supervisionList"));
        m.put("yellow", sup.get("watchList"));
        m.put("pushRed", sup.get("pushRed"));
        m.put("delayedByCategory", ach.get("delayedByCategory"));
        m.put("ranking", reportService.ranking(projectId));
        return m;
    }

    // ==================== 工具 ====================

    private static LocalDate toDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(v.toString().substring(0, 10));
    }
}
