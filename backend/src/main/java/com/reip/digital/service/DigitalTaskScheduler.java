package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.entity.DigTask;
import com.reip.digital.entity.DigTaskRun;
import com.reip.digital.entity.DigWorkflow;
import com.reip.digital.entity.DigWorkflowRun;
import com.reip.digital.mapper.DigitalMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数字员工任务调度器（30 秒巡检一次）：
 *  1. SCHEDULED：next_run_at 到期 → 执行，并推进下次时间；
 *  2. CONDITIONAL：冷却期外且条件指标满足（真实取数比较）→ 执行。
 *  幂等保护：同任务存在 RUNNING 执行记录时不重复触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalTaskScheduler {

    /** 漏跑补偿窗口：停机错过超过 15 分钟的 CRON 槽位不补跑，只顺延（避免恢复后执行过期数据） */
    private static final long CATCHUP_MINUTES = 15;

    private final DigitalMappers.TaskMapper taskMapper;
    private final DigitalMappers.EmployeeMapper employeeMapper;
    private final DigitalMappers.SkillMapper skillMapper;
    private final DigitalMappers.TaskRunMapper runMapper;
    private final DigitalMappers.WorkflowMapper wfMapper;
    private final DigitalMappers.WorkflowStepMapper wfStepMapper;
    private final DigitalMappers.WorkflowRunMapper wfRunMapper;
    private final DigitalExecutor executor;
    private final DigitalWorkflowExecutor wfExecutor;
    private final DigitalDataFetcher fetcher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 30000)
    public void tick() {
        try {
            scanScheduled();
        } catch (Exception e) {
            log.warn("[digital] 定时任务巡检异常 {}", e.getMessage());
        }
        try {
            scanConditional();
        } catch (Exception e) {
            log.warn("[digital] 条件任务巡检异常 {}", e.getMessage());
        }
        try {
            scanWorkflows();
        } catch (Exception e) {
            log.warn("[digital] 定时工作流巡检异常 {}", e.getMessage());
        }
    }

    private void scanScheduled() {
        LocalDateTime now = LocalDateTime.now();
        List<DigTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<DigTask>()
                .eq(DigTask::getTriggerType, "SCHEDULED")
                .eq(DigTask::getStatus, "ACTIVE")
                .isNotNull(DigTask::getCronExpr).ne(DigTask::getCronExpr, ""));
        for (DigTask t : tasks) {
            try {
                if (isBusy(t.getId())) continue;
                if (t.getNextRunAt() == null) {
                    // 首次调度：只登记下次时间，不立即执行
                    t.setNextRunAt(CronExpression.parse(t.getCronExpr().trim()).next(now));
                    taskMapper.updateById(t);
                    continue;
                }
                if (t.getNextRunAt().isAfter(now)) continue;
                // 漏跑补偿限制：槽位已过期超过窗口 → 只顺延不补跑
                if (now.minusMinutes(CATCHUP_MINUTES).isAfter(t.getNextRunAt())) {
                    log.info("[digital] 定时任务槽位过期超{}分钟，顺延不补跑 taskId={}", CATCHUP_MINUTES, t.getId());
                    t.setNextRunAt(CronExpression.parse(t.getCronExpr().trim()).next(now));
                    taskMapper.updateById(t);
                    continue;
                }
                if (isEmployeeInactive(t.getEmployeeId())) {
                    log.debug("[digital] 定时任务跳过（员工休假）taskId={} name={}", t.getId(), t.getName());
                    // 休假期间顺延到下一个 CRON 槽位，避免恢复后连续补跑
                    t.setNextRunAt(CronExpression.parse(t.getCronExpr().trim()).next(now));
                    taskMapper.updateById(t);
                    continue;
                }
                // 先推进下次执行时间（乐观触发），执行失败也不影响下轮调度
                LocalDateTime next = CronExpression.parse(t.getCronExpr().trim()).next(now);
                t.setNextRunAt(next);
                t.setLastRunAt(now);
                taskMapper.updateById(t);
                log.info("[digital] 定时触发 taskId={} name={} next={}", t.getId(), t.getName(), next);
                executor.execute(t.getId(), "SCHEDULED");
            } catch (Exception e) {
                // 单任务异常不阻断其余任务的巡检
                log.warn("[digital] 定时任务触发失败 taskId={} err={}", t.getId(), e.getMessage());
            }
        }
    }

    private void scanConditional() {
        LocalDateTime now = LocalDateTime.now();
        List<DigTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<DigTask>()
                .eq(DigTask::getTriggerType, "CONDITIONAL")
                .eq(DigTask::getStatus, "ACTIVE")
                .isNotNull(DigTask::getConditionExpr));
        for (DigTask t : tasks) {
            try {
                if (isBusy(t.getId())) continue;
                if (isEmployeeInactive(t.getEmployeeId())) continue;
                int cooldown = t.getCooldownMinutes() == null ? 60 : t.getCooldownMinutes();
                if (t.getLastRunAt() != null && t.getLastRunAt().plusMinutes(cooldown).isAfter(now)) continue;

                DigSkill skill = skillMapper.selectById(t.getSkillId());
                if (skill == null) continue;
                Map<String, Object> data;
                try {
                    data = fetcher.fetch(skill.getDataSource());
                } catch (Exception e) {
                    log.warn("[digital] 条件取数失败 task={} err={}", t.getId(), e.getMessage());
                    continue;
                }
                if (conditionMet(t.getConditionExpr(), data)) {
                    // 触发时即登记执行时间（冷却期基准），避免执行期间重复触发
                    t.setLastRunAt(now);
                    taskMapper.updateById(t);
                    log.info("[digital] 条件触发 taskId={} name={} cond={}", t.getId(), t.getName(), t.getConditionExpr());
                    executor.execute(t.getId(), "CONDITIONAL");
                }
            } catch (Exception e) {
                log.warn("[digital] 条件任务触发失败 taskId={} err={}", t.getId(), e.getMessage());
            }
        }
    }

    /** 定时工作流巡检：next_run_at 到期 → 触发（含 15 分钟漏跑补偿窗口） */
    private void scanWorkflows() {
        LocalDateTime now = LocalDateTime.now();
        List<DigWorkflow> wfs = wfMapper.selectList(new LambdaQueryWrapper<DigWorkflow>()
                .eq(DigWorkflow::getStatus, "ACTIVE")
                .isNotNull(DigWorkflow::getCronExpr).ne(DigWorkflow::getCronExpr, ""));
        for (DigWorkflow wf : wfs) {
            try {
                if (wf.getNextRunAt() == null) {
                    wf.setNextRunAt(CronExpression.parse(wf.getCronExpr().trim()).next(now));
                    wfMapper.updateById(wf);
                    continue;
                }
                if (wf.getNextRunAt().isAfter(now)) continue;
                // 漏跑补偿限制
                if (now.minusMinutes(CATCHUP_MINUTES).isAfter(wf.getNextRunAt())) {
                    wf.setNextRunAt(CronExpression.parse(wf.getCronExpr().trim()).next(now));
                    wfMapper.updateById(wf);
                    continue;
                }
                // 并发守卫：已有运行中/待审批的执行则顺延
                Long active = wfRunMapper.selectCount(new LambdaQueryWrapper<DigWorkflowRun>()
                        .eq(DigWorkflowRun::getWfId, wf.getId())
                        .in(DigWorkflowRun::getStatus, "RUNNING", "PAUSED"));
                if (active != null && active > 0) {
                    wf.setNextRunAt(CronExpression.parse(wf.getCronExpr().trim()).next(now));
                    wfMapper.updateById(wf);
                    continue;
                }
                LocalDateTime next = CronExpression.parse(wf.getCronExpr().trim()).next(now);
                wf.setNextRunAt(next);
                wfMapper.updateById(wf);
                log.info("[digital] 定时工作流触发 wfId={} name={} next={}", wf.getId(), wf.getName(), next);
                // 调度线程无用户上下文：临时置系统用户以填充 createdBy
                com.reip.framework.auth.LoginUser sys = new com.reip.framework.auth.LoginUser(1L, "system", "系统调度", 1L);
                com.reip.framework.auth.UserContext.set(sys);
                try {
                    wfExecutor.execute(wf.getId(), "SCHEDULED");
                } finally {
                    com.reip.framework.auth.UserContext.clear();
                }
            } catch (Exception e) {
                log.warn("[digital] 定时工作流触发失败 wfId={} err={}", wf.getId(), e.getMessage());
            }
        }
    }

    private boolean isEmployeeInactive(Long employeeId) {
        if (employeeId == null) return false;
        var emp = employeeMapper.selectById(employeeId);
        return emp == null || !"ACTIVE".equals(emp.getStatus());
    }

    /** 条件表达式 {"source":"key","op":">=","value":1} 对真实数据求值 */
    public boolean conditionMet(String exprJson, Map<String, Object> data) {
        try {
            JsonNode node = objectMapper.readTree(exprJson);
            String source = node.path("source").asText("");
            String op = node.path("op").asText("==");
            double expect = node.path("value").asDouble(Double.NaN);
            Object actualObj = data.get(source);
            if (actualObj == null) return false;
            double actual;
            try {
                actual = new BigDecimal(actualObj.toString()).doubleValue();
            } catch (Exception e) {
                return false;
            }
            return switch (op) {
                case ">=" -> actual >= expect;
                case ">" -> actual > expect;
                case "<=" -> actual <= expect;
                case "<" -> actual < expect;
                default -> Double.compare(actual, expect) == 0;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBusy(Long taskId) {
        Long c = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>()
                .eq(DigTaskRun::getTaskId, taskId).eq(DigTaskRun::getStatus, "RUNNING"));
        return c != null && c > 0;
    }
}
