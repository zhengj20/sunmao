package com.reip.digital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.ai.entity.AiKbDocument;
import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.mapper.AiMappers;
import com.reip.ai.service.AiKbService;
import com.reip.ai.service.AiLlmGateway;
import com.reip.ai.service.AiLlmResult;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.DigEmployee;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.entity.DigTask;
import com.reip.digital.entity.DigTaskRun;
import com.reip.digital.mapper.DigitalMappers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数字员工执行引擎：
 *  手动/定时/条件 → 同一入口 execute()，异步执行并逐步留痕（steps JSON），
 *  配置了大模型 → LLM 智能分析（ai_mode=LLM）；未配置/失败 → 规则引擎兜底（ai_mode=RULE），绝不空手而归。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalExecutor {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DigitalMappers.TaskMapper taskMapper;
    private final DigitalMappers.EmployeeMapper employeeMapper;
    private final DigitalMappers.SkillMapper skillMapper;
    private final DigitalMappers.TaskRunMapper runMapper;
    private final AiMappers.ModelConfigMapper modelMapper;
    private final AiLlmGateway gateway;
    private final DigitalDataFetcher fetcher;
    private final AiKbService kbService;
    private final DigitalToolService toolService;
    private final ObjectMapper objectMapper;

    private final ThreadPoolTaskExecutor pool = buildPool();

    private static ThreadPoolTaskExecutor buildPool() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(50);
        e.setThreadNamePrefix("dig-exec-");
        e.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        e.initialize();
        return e;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
    }

    /** 执行任务（异步），立即返回执行记录ID供前端轮询过程 */
    public Long execute(Long taskId, String trigger) {
        return execute(taskId, trigger, null);
    }

    /**
     * 执行任务（异步）。wfContext=工作流上游环节产物（接力注入下游 Prompt，可为空）。
     */
    public Long execute(Long taskId, String trigger, String wfContext) {
        return execute(taskId, trigger, wfContext, null);
    }

    /**
     * 执行任务（异步）。extraKbDocIds=工作流级绑定的知识库文档（逗号分隔ID，可为空）；
     * 员工级绑定（dig_employee.kb_doc_ids）在 doExecute 内自动读取。
     */
    public Long execute(Long taskId, String trigger, String wfContext, String extraKbDocIds) {
        DigTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException("任务不存在");
        DigEmployee emp = employeeMapper.selectById(task.getEmployeeId());
        if (emp == null) throw new BizException("所属数字员工不存在");
        if (!"ACTIVE".equals(emp.getStatus())) throw new BizException("数字员工已休假（停用），无法执行任务");
        DigSkill skill = skillMapper.selectById(task.getSkillId());
        if (skill == null) throw new BizException("任务绑定的技能不存在");

        // 并发守卫：同任务已有执行在途时，直接复用该执行记录（避免重复扣模型/重复留痕）
        DigTaskRun busy = runMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DigTaskRun>()
                .eq(DigTaskRun::getTaskId, taskId).eq(DigTaskRun::getStatus, "RUNNING")
                .orderByDesc(DigTaskRun::getId).last("LIMIT 1"));
        if (busy != null) return busy.getId();

        DigTaskRun run = new DigTaskRun();
        run.setRunNo("RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        run.setTaskId(task.getId());
        run.setEmployeeId(emp.getId());
        run.setTaskName(task.getName());
        run.setSkillName(skill.getName());
        run.setTriggerType(trigger == null ? "MANUAL" : trigger);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setSteps("[]");
        runMapper.insert(run);

        pool.execute(() -> doExecute(task, emp, skill, run, wfContext, extraKbDocIds));
        return run.getId();
    }

    public DigTaskRun runDetail(Long runId) {
        DigTaskRun r = runMapper.selectById(runId);
        if (r == null) throw new BizException("执行记录不存在");
        return r;
    }

    // ==================== 执行主流程（逐步留痕） ====================

    private void doExecute(DigTask task, DigEmployee emp, DigSkill skill, DigTaskRun run, String wfContext, String extraKbDocIds) {
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean failed = false;
        String errorMsg = null;
        String modelName = null;
        String aiMode = "RULE";
        String output = null;
        int tokenIn = 0, tokenOut = 0;
        String ctx = (wfContext == null || wfContext.isBlank()) ? null : wfContext.trim();

        try {
            step(steps, run, "任务启动", "SUCCESS",
                    "员工「" + emp.getName() + "」开始执行任务「" + task.getName() + "」（触发方式：" + triggerLabel(run.getTriggerType()) + "）");

            // 1. 技能装载
            step(steps, run, "技能装载", "SUCCESS",
                    "技能「" + skill.getName() + "」装载完成，提示词模板 "
                            + (skill.getPromptTemplate() == null ? 0 : skill.getPromptTemplate().length()) + " 字，取数域 "
                            + (skill.getDataSource() == null ? "NONE" : skill.getDataSource()));

            // 2. 业务数据采集
            Map<String, Object> data = fetcher.fetch(skill.getDataSource());
            step(steps, run, "业务数据采集", data.containsKey("note") ? "WARN" : "SUCCESS",
                    data.containsKey("note") ? String.valueOf(data.get("note"))
                            : "采集到 " + data.size() + " 项指标：" + dataDigest(data));

            // 3. 知识库检索（可选，技能配置 kbSearch=1 时启用）
            List<AiKbDocument> kbDocs = List.of();
            if (skill.getKbSearch() == null || skill.getKbSearch() == 1) {
                String kbKw = (skill.getName() == null ? "" : skill.getName()) + " " + (task.getName() == null ? "" : task.getName());
                kbDocs = kbService.searchForContext(kbKw, null, 3);
            }
            step(steps, run, "知识库检索", kbDocs.isEmpty() ? "WARN" : "SUCCESS",
                    kbDocs.isEmpty() ? "未匹配到相关制度文档"
                            : "命中 " + kbDocs.size() + " 篇文档：" + kbDocs.stream().map(d -> d.getTitle()).reduce((a, b) -> a + "、" + b).orElse(""));

            // 3.1 知识库绑定（员工级/工作流级显式绑定的具体文档，整篇注入提示词）
            List<AiKbDocument> boundDocs = loadBoundDocs(emp, extraKbDocIds);
            step(steps, run, "知识库绑定", boundDocs.isEmpty() ? "WARN" : "SUCCESS",
                    boundDocs.isEmpty() ? "未绑定具体知识库文档（可在「数字员工」或「工作流」中绑定）"
                            : "装载 " + boundDocs.size() + " 篇绑定文档："
                              + boundDocs.stream().map(d -> "《" + d.getTitle() + "》").reduce((a, b) -> a + "、" + b).orElse(""));

            // 3.5 工具调用（技能绑定工具时执行：内置工具/HTTP 工具，结果注入提示词）
            Map<String, String> toolResults = new LinkedHashMap<>();
            if (skill.getToolIds() != null && !skill.getToolIds().isBlank()) {
                int tOk = 0, tFail = 0;
                StringBuilder tDetail = new StringBuilder();
                for (String idStr : skill.getToolIds().split(",")) {
                    if (idStr.isBlank()) continue;
                    String tName = "";
                    try {
                        long tId = Long.parseLong(idStr.trim());
                        var t = toolService.byId(tId);
                        tName = t == null ? idStr : (t.getName() == null ? idStr : t.getName());
                        String out = toolService.invoke(tId, skill.getToolArgs());
                        toolResults.put(tName, out);
                        tOk++;
                        if (tDetail.length() > 0) tDetail.append("；");
                        tDetail.append(tName).append("✓");
                    } catch (Exception e) {
                        tFail++;
                        if (tDetail.length() > 0) tDetail.append("；");
                        tDetail.append(tName).append("✗(").append(e.getMessage()).append(")");
                    }
                }
                step(steps, run, "工具调用", tFail == 0 ? "SUCCESS" : "WARN",
                        tOk + " 成功 / " + tFail + " 失败：" + (tDetail.length() == 0 ? "无" : tDetail));
            }

            // 4. 提示词组装（含知识库检索 + 知识库绑定 + 工具数据 + 工作流上游产物接力）
            String system = buildSystem(emp, skill, data, task, kbDocs, boundDocs, ctx, toolResults);
            String assemblyNote = "系统提示词 " + system.length() + " 字（人设 " + (emp.getPersona() == null ? 0 : emp.getPersona().length())
                    + " 字 + 技能模板 + 实时数据"
                    + (!kbDocs.isEmpty() ? " + 知识库检索 " + kbDocs.size() + " 篇" : "")
                    + (!boundDocs.isEmpty() ? " + 知识库绑定 " + boundDocs.size() + " 篇" : "")
                    + (toolResults.isEmpty() ? "" : " + 工具数据 " + toolResults.size() + " 项")
                    + (ctx == null ? "" : " + 上游环节产出 " + ctx.length() + " 字") + "）";
            step(steps, run, "提示词组装", "SUCCESS", assemblyNote);

            // 5. 大模型推理 / 规则引擎兜底
            AiModelConfig cfg = resolveModel(emp);
            if (cfg != null) {
                String user = "任务：" + task.getName() + "\n" + safeJson(data);
                AiLlmResult res = gateway.chatWith(cfg, system, user);
                if (res.isSuccess()) {
                    aiMode = "LLM";
                    modelName = cfg.getModelName();
                    output = res.getContent();
                    tokenIn = res.getTokenIn() == null ? 0 : res.getTokenIn();
                    tokenOut = res.getTokenOut() == null ? 0 : res.getTokenOut();
                    step(steps, run, "大模型推理", "SUCCESS",
                            "模型 " + cfg.getModelName() + " 推理完成，耗时 " + res.getDurationMs() + "ms，"
                                    + "输入 " + res.getTokenIn() + " / 输出 " + res.getTokenOut() + " tokens");
                } else {
                    step(steps, run, "大模型推理", "WARN",
                            "模型调用失败（" + res.getMessage() + "），自动切换规则引擎兜底");
                }
            } else {
                step(steps, run, "大模型推理", "WARN", "未配置可用大模型，自动切换规则引擎兜底");
            }

            // 6. 结果生成（LLM 失败时用规则引擎产出确定性结论）
            if (output == null) {
                output = ruleFallback(emp, task, skill, data);
            }
            step(steps, run, "结果生成", "SUCCESS", "输出 " + output.length() + " 字（" + aiMode + " 模式）");
        } catch (Exception e) {
            failed = true;
            errorMsg = e.getMessage();
            step(steps, run, "执行异常", "FAIL", "执行中断：" + e.getMessage());
            log.warn("[digital] 执行失败 task={} run={} err={}", task.getId(), run.getId(), e.getMessage());
        }

        // 落库
        run.setFinishedAt(LocalDateTime.now());
        run.setDurationMs(System.currentTimeMillis() - t0);
        run.setStatus(failed ? "FAILED" : "DONE");
        run.setError(errorMsg);
        run.setModelName(modelName);
        run.setAiMode(aiMode);
        run.setTokenIn(tokenIn);
        run.setTokenOut(tokenOut);
        run.setOutput(output);
        runMapper.updateById(run);

        // 任务回写（最近执行时间 + 定时任务下次时间）
        DigTask fresh = taskMapper.selectById(task.getId());
        if (fresh != null) {
            fresh.setLastRunAt(LocalDateTime.now());
            if ("SCHEDULED".equals(fresh.getTriggerType()) && fresh.getCronExpr() != null) {
                try {
                    fresh.setNextRunAt(CronNext.next(fresh.getCronExpr()));
                } catch (Exception ignore) {
                    fresh.setNextRunAt(null);
                }
            }
            taskMapper.updateById(fresh);
        }
    }

    private void step(List<Map<String, Object>> steps, DigTaskRun run, String title, String status, String detail) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("seq", steps.size() + 1);
        s.put("title", title);
        s.put("status", status);
        s.put("detail", detail);
        s.put("ts", LocalDateTime.now().format(TS));
        steps.add(s);
        // 每步实时落库，前端轮询即可看到过程
        try {
            run.setSteps(objectMapper.writeValueAsString(steps));
            runMapper.updateById(run);
        } catch (Exception ignore) {
            // 留痕失败不阻塞主流程
        }
    }

    /** 汇总知识库绑定：员工级 + 工作流级（去重、保持顺序） */
    private List<AiKbDocument> loadBoundDocs(DigEmployee emp, String extraKbDocIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String s : new String[]{emp.getKbDocIds(), extraKbDocIds}) {
            if (s == null || s.isBlank()) continue;
            for (String x : s.split(",")) {
                if (x.isBlank()) continue;
                try { ids.add(Long.parseLong(x.trim())); } catch (Exception ignore) { }
            }
        }
        if (ids.isEmpty()) return List.of();
        try {
            return kbService.listByIds(new ArrayList<>(ids));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildSystem(DigEmployee emp, DigSkill skill, Map<String, Object> data, DigTask task,
                               List<AiKbDocument> kbDocs, List<AiKbDocument> boundDocs,
                               String wfContext, Map<String, String> toolResults) {
        StringBuilder sb = new StringBuilder();
        if (emp.getPersona() != null && !emp.getPersona().isBlank()) {
            sb.append(emp.getPersona().trim()).append("\n\n");
        }
        // 知识库绑定（员工/工作流显式绑定的具体文档，整篇注入，优先级高于关键词检索）
        if (boundDocs != null && !boundDocs.isEmpty()) {
            sb.append("【知识库（绑定文档）】\n");
            for (AiKbDocument doc : boundDocs) {
                String c = doc.getContent() == null ? "" : doc.getContent();
                if (c.length() > 1200) c = c.substring(0, 1200) + "…（已截断）";
                sb.append("《").append(doc.getTitle()).append("》\n").append(c).append("\n\n");
            }
        }
        // 工作流上游环节产物（接力）：供本环节承接分析
        if (wfContext != null && !wfContext.isBlank()) {
            String c = wfContext.trim();
            if (c.length() > 1600) c = c.substring(0, 1600) + "…（已截断）";
            sb.append("【工作流上游环节产出】\n").append(c).append("\n\n");
        }
        // 工具调用结果（计算器/业务快照/HTTP 工具）
        if (toolResults != null && !toolResults.isEmpty()) {
            sb.append("【工具数据】\n");
            for (Map.Entry<String, String> e : toolResults.entrySet()) {
                String v = e.getValue();
                if (v.length() > 800) v = v.substring(0, 800) + "…";
                sb.append("- ").append(e.getKey()).append("：").append(v).append("\n");
            }
            sb.append("\n");
        }
        // 知识库上下文
        if (kbDocs != null && !kbDocs.isEmpty()) {
            sb.append("【相关制度文档】\n");
            for (AiKbDocument doc : kbDocs) {
                sb.append("- ").append(doc.getTitle()).append("：");
                String c = doc.getContent() == null ? "" : doc.getContent();
                sb.append(c.length() > 200 ? c.substring(0, 200) + "…" : c).append("\n");
            }
            sb.append("\n");
        }
        String tpl = skill.getPromptTemplate() == null ? "" : skill.getPromptTemplate();
        tpl = tpl.replace("{{data}}", safeJson(data))
                 .replace("{{taskName}}", task.getName() == null ? "" : task.getName())
                 .replace("{{prev}}", wfContext == null ? "（无上游产出）" : wfContext.trim().substring(0, Math.min(1200, wfContext.trim().length())));
        sb.append(tpl);
        return sb.toString();
    }

    /** 规则引擎兜底：基于真实采集数据产出确定性结论（不编造） */
    private String ruleFallback(DigEmployee emp, DigTask task, DigSkill skill, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(emp.getName()).append("·规则引擎报告】\n");
        sb.append("任务「").append(task.getName()).append("」（技能：").append(skill.getName()).append("）已完成数据采集，关键指标如下：\n");
        if (data == null || data.isEmpty()) {
            sb.append("（本次未采集到业务数据）\n");
        }
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if ("note".equals(e.getKey())) continue;
            sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append("\n");
        }
        sb.append("\n提示：以上为规则引擎确定性输出。在「模型配置」中启用 DeepSeek/GLM/Kimi/通义/本地模型后，将自动升级为智能分析。");
        return sb.toString();
    }

    private String dataDigest(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (n++ > 0) sb.append("，");
            sb.append(e.getKey()).append("=").append(e.getValue());
            if (n >= 5) {
                sb.append("…");
                break;
            }
        }
        return sb.toString();
    }

    private AiModelConfig resolveModel(DigEmployee emp) {
        if (emp.getModelConfigId() != null) {
            AiModelConfig m = modelMapper.selectById(emp.getModelConfigId());
            if (m != null && "ACTIVE".equals(m.getStatus())) return m;
        }
        return gateway.findActive();
    }

    private String triggerLabel(String trigger) {
        return switch (trigger == null ? "" : trigger) {
            case "SCHEDULED" -> "定时";
            case "CONDITIONAL" -> "条件触发";
            default -> "手动";
        };
    }

    private String safeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    // ==================== CRON 下次时间（内聚工具） ====================

    private static final class CronNext {
        static LocalDateTime next(String cron) {
            return org.springframework.scheduling.support.CronExpression.parse(cron.trim()).next(LocalDateTime.now());
        }
    }
}
