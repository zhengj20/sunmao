package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.service.AiLlmGateway;
import com.reip.ai.service.AiLlmResult;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.*;
import com.reip.digital.mapper.DigitalMappers;
import com.reip.framework.auth.UserContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工作流执行器（借鉴成熟数字员工系统增强）：
 *  - 环节接力：use_prev=1 时，上一环节完整产物作为「工作流上游环节产出」注入下一环节 Prompt；
 *  - 人工审批门：requires_approval=1 的环节执行到此暂停（PAUSED），批准后从该环节续跑，驳回则终止（REJECTED）；
 *  - 整合交付：全部环节完成后生成整合报告（LLM 可用时智能整合，否则直拼各环产物）；
 *  - 每步增量落库，前端轮询即可看到实时进度。
 */
@Slf4j
@Service
public class DigitalWorkflowExecutor {

    private final DigitalMappers.WorkflowMapper wfMapper;
    private final DigitalMappers.WorkflowStepMapper stepMapper;
    private final DigitalMappers.WorkflowRunMapper runMapper;
    private final DigitalMappers.TaskRunMapper taskRunMapper;
    private final DigitalMappers.TaskMapper taskMapper;
    private final DigitalMappers.WfApprovalMapper approvalMapper;
    private final DigitalExecutor taskExecutor;
    private final AiLlmGateway gateway;
    private final ObjectMapper objectMapper;

    private final ThreadPoolTaskExecutor pool;

    public DigitalWorkflowExecutor(DigitalMappers.WorkflowMapper wfMapper,
                                    DigitalMappers.WorkflowStepMapper stepMapper,
                                    DigitalMappers.WorkflowRunMapper runMapper,
                                    DigitalMappers.TaskRunMapper taskRunMapper,
                                    DigitalMappers.TaskMapper taskMapper,
                                    DigitalMappers.WfApprovalMapper approvalMapper,
                                    DigitalExecutor taskExecutor,
                                    AiLlmGateway gateway,
                                    ObjectMapper objectMapper) {
        this.wfMapper = wfMapper;
        this.stepMapper = stepMapper;
        this.runMapper = runMapper;
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.approvalMapper = approvalMapper;
        this.taskExecutor = taskExecutor;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        ThreadPoolTaskExecutor p = new ThreadPoolTaskExecutor();
        p.setCorePoolSize(1);
        p.setMaxPoolSize(2);
        p.setQueueCapacity(10);
        p.setThreadNamePrefix("wf-exec-");
        p.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        p.initialize();
        this.pool = p;
    }

    @PreDestroy
    public void shutdown() { pool.shutdown(); }

    /** 触发工作流执行，返回 runId */
    public Long execute(Long wfId, String trigger) {
        DigWorkflow wf = wfMapper.selectById(wfId);
        if (wf == null || !"ACTIVE".equals(wf.getStatus())) throw new BizException("工作流不存在或已停用");

        List<DigWorkflowStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<DigWorkflowStep>()
                        .eq(DigWorkflowStep::getWfId, wfId).orderByAsc(DigWorkflowStep::getStepOrder));
        if (steps.isEmpty()) throw new BizException("工作流没有步骤");

        // 并发守卫：同一工作流已有运行中/待审批的执行时不重复触发
        Long active = runMapper.selectCount(new LambdaQueryWrapper<DigWorkflowRun>()
                .eq(DigWorkflowRun::getWfId, wfId)
                .in(DigWorkflowRun::getStatus, "RUNNING", "PAUSED"));
        if (active != null && active > 0) {
            throw new BizException("该工作流已有执行进行中（或正在等待人工审批），请完成后再触发");
        }

        DigWorkflowRun run = new DigWorkflowRun();
        run.setWfId(wfId);
        run.setWfName(wf.getName());
        run.setStatus("RUNNING");
        run.setCurrentStep(0);
        run.setTotalSteps(steps.size());
        run.setFailedSteps(0);
        run.setStartedAt(LocalDateTime.now());
        run.setCreatedBy(UserContext.require().getUserId());
        runMapper.insert(run);

        Long runId = run.getId();
        pool.execute(() -> doExecute(wf, steps, run, 0, new ArrayList<>(), 0, null));
        return runId;
    }

    /**
     * 审批决策：批准 → 从该环节继续执行；驳回 → 工作流终止（REJECTED）
     */
    public void decide(Long approvalId, boolean approve, String comment, Long userId) {
        DigWfApproval a = approvalMapper.selectById(approvalId);
        if (a == null || !"PENDING".equals(a.getStatus())) {
            throw new BizException("审批记录不存在或已处理");
        }
        a.setStatus(approve ? "APPROVED" : "REJECTED");
        a.setComment(comment);
        a.setDecidedBy(userId);
        a.setDecidedAt(LocalDateTime.now());
        approvalMapper.updateById(a);

        DigWorkflowRun run = runMapper.selectById(a.getWfRunId());
        if (run == null) return;

        if (!approve) {
            run.setStatus("REJECTED");
            run.setFinishedAt(LocalDateTime.now());
            run.setError("环节「" + a.getStepName() + "」被驳回" + (comment == null ? "" : "（" + comment + "）"));
            runMapper.updateById(run);
            log.info("[workflow] run={} 环节{} 被驳回，工作流终止", run.getId(), a.getStepOrder());
            return;
        }

        DigWorkflow wf = wfMapper.selectById(run.getWfId());
        if (wf == null) return;
        List<DigWorkflowStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<DigWorkflowStep>()
                        .eq(DigWorkflowStep::getWfId, wf.getId()).orderByAsc(DigWorkflowStep::getStepOrder));
        int startIdx = Math.min(a.getStepOrder() - 1, Math.max(0, steps.size() - 1));

        // 恢复已完成环节的结果（续跑用）
        List<Map<String, Object>> prev = parseSteps(run.getSteps());
        int failed = run.getFailedSteps() == null ? 0 : run.getFailedSteps();

        // 重建上游产物（接力）：取续跑点之前最近一个已完成环节的产出
        String resumePrevOutput = null;
        for (int k = startIdx - 1; k >= 0; k--) {
            if (k >= prev.size()) continue;
            Object trId = prev.get(k).get("taskRunId");
            if (trId == null) continue;
            String out = stepOutput(Long.valueOf(trId.toString()));
            if (out != null && !out.isBlank()) { resumePrevOutput = out; break; }
        }
        final String relayCtx = resumePrevOutput;

        run.setStatus("RUNNING");
        runMapper.updateById(run);
        log.info("[workflow] run={} 环节{} 审批通过，从该环节继续（上游产物 {}字）",
                run.getId(), a.getStepOrder(), relayCtx == null ? 0 : relayCtx.length());
        pool.execute(() -> doExecute(wf, steps, run, startIdx, prev, failed, relayCtx));
    }

    /** 工作流主流程 */
    private void doExecute(DigWorkflow wf, List<DigWorkflowStep> steps, DigWorkflowRun run,
                           int startIdx, List<Map<String, Object>> stepResults, int failedSteps, String prevOutput) {
        boolean allDone = true;
        try {
            for (int i = startIdx; i < steps.size(); i++) {
                DigWorkflowStep step = steps.get(i);

                // ===== 人工审批门：执行到此暂停，等待批准/驳回（已批准的环节续跑时跳过） =====
                if (step.getRequiresApproval() != null && step.getRequiresApproval() == 1 && !isApproved(run.getId(), step.getStepOrder())) {
                    run.setCurrentStep(i + 1);
                    run.setStatus("PAUSED");
                    runMapper.updateById(run);

                    DigWfApproval ap = new DigWfApproval();
                    ap.setWfRunId(run.getId());
                    ap.setStepOrder(step.getStepOrder());
                    ap.setStepName(resolveStepName(step));
                    ap.setStatus("PENDING");
                    approvalMapper.insert(ap);
                    log.info("[workflow] run={} 暂停于环节{} 等待人工审批（approvalId={}）", run.getId(), step.getStepOrder(), ap.getId());
                    return; // 线程结束；批准后由 decide() 续跑
                }

                run.setCurrentStep(i + 1);
                runMapper.updateById(run);

                Map<String, Object> sr = new LinkedHashMap<>();
                sr.put("order", step.getStepOrder());
                sr.put("name", resolveStepName(step));
                sr.put("taskId", step.getTaskId());
                sr.put("onFail", step.getOnFail() != null ? step.getOnFail() : "STOP");
                boolean relay = step.getUsePrev() == null || step.getUsePrev() == 1;
                long stepStart = System.currentTimeMillis();
                String ctx = (relay && prevOutput != null && !prevOutput.isBlank()) ? prevOutput : null;
                if (ctx != null) sr.put("relayedChars", ctx.length());

                // 执行任务（携带上游产物接力上下文 + 工作流级知识库绑定）
                try {
                    Long taskRunId = taskExecutor.execute(step.getTaskId(), "WORKFLOW", ctx, wf.getKbDocIds());
                    sr.put("taskRunId", taskRunId);

                    String taskStatus = waitForCompletion(taskRunId, step.getTimeoutSec() != null ? step.getTimeoutSec() : 120);
                    sr.put("status", taskStatus);
                    if (!"DONE".equals(taskStatus)) {
                        failedSteps++;
                    }
                    if ("TIMEOUT".equals(taskStatus)) {
                        sr.put("error", "等待任务完成超时（" + (step.getTimeoutSec() != null ? step.getTimeoutSec() : 120) + "s），任务可能仍在后台执行");
                    }

                    if (!"DONE".equals(taskStatus)) {
                        String onFail = step.getOnFail() != null ? step.getOnFail() : "STOP";
                        if ("STOP".equals(onFail)) {
                            allDone = false;
                            recordStep(run, sr, stepStart, stepResults, failedSteps);
                            break;
                        }
                    } else {
                        // 成功：取该环节产物作为下游接力上下文
                        prevOutput = stepOutput(taskRunId);
                    }
                } catch (Exception e) {
                    failedSteps++;
                    sr.put("status", "FAIL");
                    sr.put("error", e.getMessage());
                    String onFail = step.getOnFail() != null ? step.getOnFail() : "STOP";
                    if ("STOP".equals(onFail)) {
                        allDone = false;
                        recordStep(run, sr, stepStart, stepResults, failedSteps);
                        break;
                    }
                }
                // 每步落库，前端轮询即可看到实时进度
                recordStep(run, sr, stepStart, stepResults, failedSteps);
            }
        } catch (Exception e) {
            allDone = false;
            run.setError(e.getMessage());
        }

        // 完成：生成整合交付（全部环节成功时）
        if (allDone) {
            try {
                String consolidated = buildConsolidated(wf, stepResults);
                if (consolidated != null) {
                    run.setOutput(consolidated);
                    log.info("[workflow] run={} 整合交付已生成（{}字）", run.getId(), consolidated.length());
                }
            } catch (Exception e) {
                log.warn("[workflow] run={} 整合交付生成失败：{}", run.getId(), e.getMessage());
            }
        }

        persistRun(run, stepResults, failedSteps);
        run.setStatus(allDone ? "DONE" : "FAIL");
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        log.info("[workflow] wf={} run={} status={} steps={} failedSteps={}", wf.getId(), run.getId(), run.getStatus(), stepResults.size(), failedSteps);
    }

    /** 该环节是否已批准（续跑时跳过审批门，避免重复暂停） */
    private boolean isApproved(Long runId, Integer stepOrder) {
        if (runId == null || stepOrder == null) return false;
        Long c = approvalMapper.selectCount(new LambdaQueryWrapper<DigWfApproval>()
                .eq(DigWfApproval::getWfRunId, runId)
                .eq(DigWfApproval::getStepOrder, stepOrder)
                .eq(DigWfApproval::getStatus, "APPROVED"));
        return c != null && c > 0;
    }

    /** 环节产物（供下游接力） */
    private String stepOutput(Long taskRunId) {
        if (taskRunId == null) return null;
        DigTaskRun tr = taskRunMapper.selectById(taskRunId);
        return tr == null ? null : tr.getOutput();
    }

    /** 整合交付：LLM 可用时智能整合，否则直拼各环产物 */
    private String buildConsolidated(DigWorkflow wf, List<Map<String, Object>> stepResults) {
        StringBuilder joined = new StringBuilder();
        for (Map<String, Object> sr : stepResults) {
            if (!"DONE".equals(sr.get("status"))) continue;
            Object trId = sr.get("taskRunId");
            if (trId == null) continue;
            String out = stepOutput(Long.valueOf(trId.toString()));
            if (out != null && !out.isBlank()) {
                joined.append("## 环节：").append(sr.get("name")).append("\n").append(out).append("\n\n");
            }
        }
        if (joined.length() == 0) return null;

        try {
            AiModelConfig cfg = gateway.findActive();
            if (cfg != null) {
                String sys = "你是集团运营助理。请把多环节工作流的产出整合为一份简明的运营报告（400字内），" +
                        "包含：总体结论、关键数据、风险提示、建议行动。只依据给定材料，不编造。";
                AiLlmResult res = gateway.chatWith(cfg, sys, "工作流：" + wf.getName() + "\n\n" + joined);
                if (res.isSuccess() && res.getContent() != null && !res.getContent().isBlank()) {
                    return "【整合交付 · " + wf.getName() + "】\n\n" + res.getContent()
                            + "\n\n---\n（各环节原始产出）\n\n" + joined;
                }
            }
        } catch (Exception ignore) {
            // LLM 整合失败 → 直拼
        }
        return "【整合交付 · " + wf.getName() + "】（各环节产物直拼）\n\n" + joined;
    }

    /** 步骤显示名：优先步骤自定义名，其次绑定任务名 */
    private String resolveStepName(DigWorkflowStep step) {
        if (step.getName() != null && !step.getName().isBlank()) return step.getName();
        if (step.getTaskId() != null) {
            DigTask t = taskMapper.selectById(step.getTaskId());
            if (t != null && t.getName() != null) return t.getName();
        }
        return "步骤" + (step.getStepOrder() == null ? "" : step.getStepOrder());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSteps(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, Object>> l = objectMapper.readValue(json, List.class);
            return l == null ? new ArrayList<>() : new ArrayList<>(l);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 记录一个已完成/失败的步骤并增量落库 */
    private void recordStep(DigWorkflowRun run, Map<String, Object> sr, long stepStart,
                            List<Map<String, Object>> stepResults, int failedSteps) {
        sr.put("durationMs", System.currentTimeMillis() - stepStart);
        stepResults.add(sr);
        persistRun(run, stepResults, failedSteps);
    }

    /** 增量持久化运行进度（steps JSON + 失败步骤数） */
    private void persistRun(DigWorkflowRun run, List<Map<String, Object>> stepResults, int failedSteps) {
        try {
            run.setSteps(safeJson(stepResults));
            run.setFailedSteps(failedSteps);
            runMapper.updateById(run);
        } catch (Exception ignore) {
            // 进度留痕失败不阻塞主流程
        }
    }

    private String waitForCompletion(Long taskRunId, int timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            DigTaskRun tr = taskRunMapper.selectById(taskRunId);
            if (tr != null && ("DONE".equals(tr.getStatus()) || "FAILED".equals(tr.getStatus()))) {
                return tr.getStatus();
            }
            Thread.sleep(1000);
        }
        return "TIMEOUT";
    }

    private String safeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }
}
