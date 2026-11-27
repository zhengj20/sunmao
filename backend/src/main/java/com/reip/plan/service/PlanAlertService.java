package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.framework.param.ParamService;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计划预警：红黄绿灯重算 / 滞后任务下游连锁影响分析 / 红灯推送集团运营R3 + 滞后专项督办清单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanAlertService {

    private final PlanMappers.TaskMapper taskMapper;
    private final ParamService paramService;

    /** 重算项目全部任务预警颜色（完成=GREEN；滞后<=阈值=YELLOW；>阈值=RED） */
    @Transactional
    public int recomputeAll(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        int yellow = paramService.getInt("plan.alert.yellow_days", 14);
        LocalDate today = LocalDate.now();
        List<PlanTask> list = taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId));
        int changed = 0;
        for (PlanTask t : list) {
            String color = PlanSupport.alertColor(t, today, yellow);
            if (!color.equals(t.getAlertStatus())) {
                t.setAlertStatus(color);
                if ("RED".equals(color) && !"COMPLETED".equals(t.getStatus()) && !"DELAYED".equals(t.getStatus()))
                    t.setStatus("DELAYED");
                if (taskMapper.updateById(t) > 0) changed++;
            }
        }
        return changed;
    }

    /** 滞后任务清单（YELLOW/RED，按滞后天数降序） */
    public List<Map<String, Object>> delayed(Long projectId) {
        List<PlanTask> list = tasks(projectId);
        int yellow = paramService.getInt("plan.alert.yellow_days", 14);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlanTask t : list) {
            long d = PlanSupport.overdueDays(t, today);
            if (d <= 0 || "COMPLETED".equals(t.getStatus())) continue;
            Map<String, Object> m = view(t);
            m.put("overdueDays", d);
            m.put("alertStatus", PlanSupport.alertColor(t, today, yellow));
            rows.add(m);
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("overdueDays")).reversed());
        return rows;
    }

    /** 滞后连锁影响分析：本任务 → 直接/间接下游受影响节点清单 */
    public Map<String, Object> chainImpact(Long taskId) {
        PlanTask t = require(taskId);
        List<PlanTask> all = tasks(t.getProjectId());
        Set<Long> affected = new LinkedHashSet<>();
        // BFS 沿前置依赖反向展开（谁依赖我/依赖依赖我的人）
        Deque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(t.getId());
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            for (PlanTask c : all) {
                if (c.getId().equals(t.getId())) continue;
                if (PlanSupport.preIds(c.getPreTaskIds()).contains(cur) && affected.add(c.getId())) {
                    queue.add(c.getId());
                }
            }
        }
        List<Map<String, Object>> downstream = new ArrayList<>();
        for (Long id : affected) {
            PlanTask c = taskMapper.selectById(id);
            if (c != null) downstream.add(view(c));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", t.getId());
        r.put("taskName", t.getTaskName());
        r.put("overdueDays", PlanSupport.overdueDays(t, LocalDate.now()));
        r.put("downstreamCount", downstream.size());
        r.put("downstream", downstream);
        return r;
    }

    /**
     * R3 红灯推送集团运营 + 滞后专项督办清单
     * 当前为规则实现（按阈值判定 RED 并纳入督办清单 + 记录推送留痕），标注可接 ai-service 生成督办话术
     */
    public Map<String, Object> supervision(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        int yellow = paramService.getInt("plan.alert.yellow_days", 14);
        LocalDate today = LocalDate.now();
        boolean push = paramService.getBool("plan.alert.push_red", true);
        List<Map<String, Object>> red = new ArrayList<>();
        List<Map<String, Object>> yellowList = new ArrayList<>();
        for (PlanTask t : tasks(projectId)) {
            if ("COMPLETED".equals(t.getStatus())) continue;
            long d = PlanSupport.overdueDays(t, today);
            if (d <= 0) continue;
            String color = PlanSupport.alertColor(t, today, yellow);
            Map<String, Object> m = view(t);
            m.put("overdueDays", d);
            m.put("alertStatus", color);
            if ("RED".equals(color)) {
                if (push) {
                    // 推送集团运营（当前规则实现：留痕；后续可接 ai-service 生成督办通知）
                    log.info("[plan][R3] 红灯推送集团运营 task={} name={} overdue={}d", t.getId(), t.getTaskName(), d);
                    m.put("pushed", true);
                    m.put("pushTarget", "集团运营(ADMIN)");
                }
                red.add(m);
            } else {
                yellowList.add(m);
            }
        }
        red.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("overdueDays")).reversed());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("redCount", red.size());
        r.put("yellowCount", yellowList.size());
        r.put("supervisionList", red);
        r.put("watchList", yellowList);
        r.put("pushRed", push);
        return r;
    }

    // ==================== 私有 ====================

    private List<PlanTask> tasks(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        return taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId).orderByAsc(PlanTask::getId));
    }

    private Map<String, Object> view(PlanTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskCode", t.getTaskCode());
        m.put("taskName", t.getTaskName());
        m.put("isMilestone", PlanSupport.isMilestone(t) ? 1 : 0);
        m.put("specialty", t.getSpecialty());
        m.put("planStart", t.getPlanStart());
        m.put("planEnd", t.getPlanEnd());
        m.put("baselineEnd", t.getBaselineEnd());
        m.put("progressPct", PlanSupport.nvl(t.getProgressPct()));
        m.put("respUserName", t.getRespUserName());
        m.put("status", t.getStatus());
        return m;
    }

    private PlanTask require(Long id) {
        if (id == null) throw new BizException("缺少任务ID");
        PlanTask t = taskMapper.selectById(id);
        if (t == null) throw new BizException("计划任务不存在");
        return t;
    }
}
