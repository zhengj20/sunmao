package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.DigEmployee;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.entity.DigTask;
import com.reip.digital.entity.DigTaskRun;
import com.reip.digital.mapper.DigitalBizMapper;
import com.reip.digital.mapper.DigitalMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务定义管理：定时(SCHEDULED+CRON)/条件(CONDITIONAL+JSON)/手动(MANUAL)
 */
@Service
@RequiredArgsConstructor
public class DigitalTaskService {

    private final DigitalMappers.TaskMapper mapper;
    private final DigitalMappers.EmployeeMapper employeeMapper;
    private final DigitalMappers.SkillMapper skillMapper;
    private final DigitalMappers.TaskRunMapper runMapper;
    private final DigitalBizMapper bizMapper;
    private final ObjectMapper objectMapper;

    public List<DigTask> list(Long employeeId) {
        LambdaQueryWrapper<DigTask> qw = new LambdaQueryWrapper<>();
        if (employeeId != null) qw.eq(DigTask::getEmployeeId, employeeId);
        qw.orderByAsc(DigTask::getEmployeeId).orderByAsc(DigTask::getId);
        return mapper.selectList(qw);
    }

    public DigTask detail(Long id) {
        DigTask t = require(id);
        return t;
    }

    public Long create(DigTask t) {
        if (t == null) throw new BizException("缺少任务内容");
        if (t.getName() == null || t.getName().isBlank()) throw new BizException("请填写任务名称");
        if (t.getEmployeeId() == null) throw new BizException("请选择所属数字员工");
        if (t.getSkillId() == null) throw new BizException("请选择技能");
        if (t.getTriggerType() == null || t.getTriggerType().isBlank()) t.setTriggerType("MANUAL");
        validate(t);
        if (t.getTaskCode() == null || t.getTaskCode().isBlank()) {
            t.setTaskCode("TASK-" + System.currentTimeMillis());
        }
        if (t.getStatus() == null) t.setStatus("ACTIVE");
        if (t.getCooldownMinutes() == null) t.setCooldownMinutes(60);
        t.setId(null);
        if (t.getTriggerType().equals("SCHEDULED")) {
            t.setNextRunAt(nextCron(t.getCronExpr(), LocalDateTime.now()));
        } else {
            t.setNextRunAt(null);
        }
        t.setLastRunAt(null);
        mapper.insert(t);
        return t.getId();
    }

    public void update(Long id, DigTask t) {
        DigTask cur = require(id);
        if (t.getName() != null) cur.setName(t.getName());
        if (t.getEmployeeId() != null) cur.setEmployeeId(t.getEmployeeId());
        if (t.getSkillId() != null) cur.setSkillId(t.getSkillId());
        if (t.getTriggerType() != null) cur.setTriggerType(t.getTriggerType());
        if (t.getCronExpr() != null) cur.setCronExpr(t.getCronExpr());
        if (t.getConditionExpr() != null) cur.setConditionExpr(t.getConditionExpr());
        if (t.getCooldownMinutes() != null) cur.setCooldownMinutes(t.getCooldownMinutes());
        if (t.getStatus() != null) cur.setStatus(t.getStatus());
        if (t.getRemark() != null) cur.setRemark(t.getRemark());
        validate(cur);
        // 切换触发方式时清理旧触发配置，避免残留的 CRON/条件被误用
        if (!"SCHEDULED".equals(cur.getTriggerType())) {
            cur.setCronExpr(null);
            cur.setNextRunAt(null);
        }
        if (!"CONDITIONAL".equals(cur.getTriggerType())) {
            cur.setConditionExpr(null);
        }
        // 重新计算下次执行时间
        if ("SCHEDULED".equals(cur.getTriggerType())) {
            cur.setNextRunAt(nextCron(cur.getCronExpr(), LocalDateTime.now()));
        } else {
            cur.setNextRunAt(null);
        }
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    /** 校验触发配置合法性（CRON 可解析 / 条件 JSON 结构） */
    public void validate(DigTask t) {
        String trig = t.getTriggerType();
        if ("SCHEDULED".equals(trig)) {
            if (t.getCronExpr() == null || t.getCronExpr().isBlank()) throw new BizException("定时任务必须填写 CRON 表达式");
            try {
                CronExpression.parse(t.getCronExpr().trim());
            } catch (Exception e) {
                throw new BizException("CRON 表达式无效（需 6 段含秒，如 0 0 9 * * 1-5）");
            }
        }
        if ("CONDITIONAL".equals(trig)) {
            if (t.getConditionExpr() == null || t.getConditionExpr().isBlank()) {
                throw new BizException("条件任务必须填写条件表达式 JSON");
            }
            try {
                JsonNode node = objectMapper.readTree(t.getConditionExpr());
                if (!node.has("source") || !node.has("op") || !node.has("value")) {
                    throw new BizException("条件 JSON 需包含 source/op/value 三个字段");
                }
            } catch (BizException be) {
                throw be;
            } catch (Exception e) {
                throw new BizException("条件表达式 JSON 解析失败");
            }
        }
        // 员工与技能必须存在
        DigEmployee emp = employeeMapper.selectById(t.getEmployeeId());
        if (emp == null) throw new BizException("所属数字员工不存在");
        DigSkill skill = skillMapper.selectById(t.getSkillId());
        if (skill == null) throw new BizException("技能不存在");
    }

    /** 计算 CRON 下一次执行时间（6 段含秒） */
    public LocalDateTime nextCron(String cron, LocalDateTime after) {
        CronExpression expr = CronExpression.parse(cron.trim());
        return expr.next(after);
    }

    /**
     * 执行统计：总量/成功率/平均耗时/LLM占比 + 近 7 天趋势。
     * 供任务中心页头概览卡片与趋势图使用。
     */
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        Long running = countRun("RUNNING");
        Long done = countRun("DONE");
        Long failed = countRun("FAILED");
        long d = done == null ? 0 : done;
        long f = failed == null ? 0 : failed;
        long total = d + f + (running == null ? 0 : running);
        out.put("total", total);
        out.put("running", running == null ? 0 : running);
        out.put("done", d);
        out.put("failed", f);
        out.put("successRate", (d + f) == 0 ? null : Math.round(d * 1000.0 / (d + f)) / 10.0);

        out.put("avgDurationMs", bizMapper.avgDurationMs());

        Long llm = countRunByMode("LLM");
        Long rule = countRunByMode("RULE");
        out.put("llmRuns", llm == null ? 0 : llm);
        out.put("ruleRuns", rule == null ? 0 : rule);
        out.put("totalTokens", bizMapper.totalTokens());

        // 近 7 天趋势（补齐无数据的日期）
        List<Map<String, Object>> trendRows = bizMapper.selectRunTrend7d();
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> r : trendRows) {
            byDay.put(String.valueOf(r.get("d")), r);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            Map<String, Object> row = byDay.get(day.toString());
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("date", day.toString().substring(5));
            cell.put("done", row == null ? 0 : num(row.get("doneCnt")));
            cell.put("failed", row == null ? 0 : num(row.get("failedCnt")));
            trend.add(cell);
        }
        out.put("trend", trend);
        return out;
    }

    private Long countRun(String status) {
        Long c = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>().eq(DigTaskRun::getStatus, status));
        return c == null ? 0 : c;
    }

    private Long countRunByMode(String mode) {
        Long c = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>()
                .eq(DigTaskRun::getAiMode, mode).ne(DigTaskRun::getStatus, "RUNNING"));
        return c == null ? 0 : c;
    }

    private long num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private DigTask require(Long id) {
        if (id == null) throw new BizException("缺少任务ID");
        DigTask t = mapper.selectById(id);
        if (t == null) throw new BizException("任务不存在");
        return t;
    }
}
