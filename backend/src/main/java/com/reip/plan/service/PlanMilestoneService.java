package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.plan.entity.PlanAdjust;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 里程碑：台账(拿地/开工/正负零/预售/开盘/竣备/交付) / 达成率统计 / 延期原因归类
 */
@Service
@RequiredArgsConstructor
public class PlanMilestoneService {

    private final PlanMappers.TaskMapper taskMapper;
    private final PlanMappers.AdjustMapper adjustMapper;

    /** 里程碑台账 */
    public List<Map<String, Object>> ledger(Long projectId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlanTask t : milestones(projectId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskCode", t.getTaskCode());
            m.put("taskName", t.getTaskName());
            m.put("planStart", t.getPlanStart());
            m.put("planEnd", t.getPlanEnd());
            m.put("baselineEnd", t.getBaselineEnd());
            m.put("actualStart", t.getActualStart());
            m.put("actualEnd", t.getActualEnd());
            m.put("progressPct", PlanSupport.nvl(t.getProgressPct()));
            m.put("status", t.getStatus());
            m.put("alertStatus", t.getAlertStatus());
            m.put("overdueDays", PlanSupport.overdueDays(t, LocalDate.now()));
            rows.add(m);
        }
        return rows;
    }

    /** 里程碑达成率统计 + 延期原因归类 */
    public Map<String, Object> achievement(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        List<PlanTask> ms = milestones(projectId);
        long total = ms.size();
        long completed = ms.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long inProgress = ms.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long delayed = ms.stream().filter(t -> "DELAYED".equals(t.getStatus())).count();
        BigDecimal rate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        // 延期原因归类：取每个滞后里程碑最近一条调整单的 reason_category
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (PlanTask t : ms) {
            if (!"DELAYED".equals(t.getStatus())) continue;
            String cat = latestCategory(t.getId());
            String key = (cat == null || cat.isBlank()) ? "未归类" : cat;
            byCategory.merge(key, 1L, Long::sum);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("total", total);
        r.put("completed", completed);
        r.put("inProgress", inProgress);
        r.put("delayed", delayed);
        r.put("achievementRate", rate);
        r.put("delayedByCategory", byCategory);
        return r;
    }

    // ==================== 私有 ====================

    private List<PlanTask> milestones(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        return taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId)
                .eq(PlanTask::getIsMilestone, 1)
                .orderByAsc(PlanTask::getId));
    }

    private String latestCategory(Long taskId) {
        List<PlanAdjust> list = adjustMapper.selectList(new LambdaQueryWrapper<PlanAdjust>()
                .eq(PlanAdjust::getTaskId, taskId)
                .orderByDesc(PlanAdjust::getId));
        for (PlanAdjust a : list) {
            if (a.getReasonCategory() != null && !a.getReasonCategory().isBlank()) return a.getReasonCategory();
        }
        return null;
    }
}
