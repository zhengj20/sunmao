package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划报表：甘特图数据 / 里程碑达成月报 / 各单位执行排名(红黄绿统计)
 */
@Service
@RequiredArgsConstructor
public class PlanReportService {

    private final PlanMappers.TaskMapper taskMapper;
    private final PlanMappers.ReportMapper reportMapper;
    private final PlanMilestoneService milestoneService;

    /** 甘特图数据：节点/计划区间/基线/实际/进度/预警色 */
    public List<Map<String, Object>> gantt(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        List<PlanTask> list = taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getProjectId, projectId).orderByAsc(PlanTask::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlanTask t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskCode", t.getTaskCode());
            m.put("taskName", t.getTaskName());
            m.put("level", t.getLevel());
            m.put("isMilestone", PlanSupport.isMilestone(t) ? 1 : 0);
            m.put("specialty", t.getSpecialty());
            m.put("planStart", t.getPlanStart());
            m.put("planEnd", t.getPlanEnd());
            m.put("baselineStart", t.getBaselineStart());
            m.put("baselineEnd", t.getBaselineEnd());
            m.put("actualStart", t.getActualStart());
            m.put("actualEnd", t.getActualEnd());
            m.put("progressPct", PlanSupport.nvl(t.getProgressPct()));
            m.put("alertStatus", t.getAlertStatus());
            m.put("status", t.getStatus());
            m.put("respUserName", t.getRespUserName());
            rows.add(m);
        }
        return rows;
    }

    /** 里程碑达成月报：台账 + 达成率 + 延期原因归类 */
    public Map<String, Object> milestone(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("month", LocalDate.now().withDayOfMonth(1).toString().substring(0, 7));
        r.put("ledger", milestoneService.ledger(projectId));
        r.put("achievement", milestoneService.achievement(projectId));
        return r;
    }

    /** 各单位执行排名：红黄绿统计 + 完成率 */
    public List<Map<String, Object>> ranking(Long projectId) {
        List<Map<String, Object>> rows = reportMapper.ranking(projectId);
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> m : rows) {
            long total = toLong(m.get("total"));
            long completed = toLong(m.get("completed"));
            long red = toLong(m.get("red"));
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("rank", rank++);
            o.put("orgId", m.get("orgId"));
            o.put("orgName", m.get("orgName") == null ? "未分配" : m.get("orgName"));
            o.put("total", total);
            o.put("green", toLong(m.get("green")));
            o.put("yellow", toLong(m.get("yellow")));
            o.put("red", red);
            o.put("completed", completed);
            o.put("onTimeRate", total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP));
            out.add(o);
        }
        return out;
    }

    private static long toLong(Object v) {
        return v == null ? 0L : new BigDecimal(v.toString()).longValue();
    }
}
