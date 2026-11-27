package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.ai.entity.AiPrompt;
import com.reip.ai.entity.AiTask;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.digital.mapper.DigitalMappers;
import com.reip.digital.service.DigitalExecutor;
import com.reip.framework.auth.UserContext;
import com.reip.portal.service.CockpitV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 报告生成。无 LLM 也能用：提示词模板 + 真实取数（CockpitV2Service）填充 + 规则结论；
 * 有 LLM 时生成"解读与建议"章节。写 ai_task（异步任务中心）+ ai_audit_log。
 */
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final CockpitV2Service cockpit;
    private final AiMappers.PromptMapper promptMapper;
    private final AiMappers.TaskMapper taskMapper;
    private final AiLlmGateway gateway;
    private final AiGuardrail guardrail;
    private final AiAuditService audit;
    private final ObjectMapper objectMapper;
    private final DigitalExecutor digitalExecutor;
    private final DigitalMappers.TaskMapper digTaskMapper;
    private final DigitalMappers.EmployeeMapper digEmployeeMapper;
    private final DigitalMappers.SkillMapper digSkillMapper;
    private final AiKbService kbService;

    public Map<String, Object> generate(String type, Long projectId, String month) {
        String norm = normalize(type);
        if (norm == null) throw new BizException("未知报告类型，可选：销售日报/成本月报/采购分析");
        String title = titleOf(norm) + (projectId != null ? "（项目" + projectId + "）" : "（集团）");
        if (month != null && !month.isBlank()) title += " " + month;

        Long configId = gateway.findActive() == null ? null : gateway.findActive().getId();
        long t0 = System.currentTimeMillis();
        Integer tokenIn = null;
        Integer tokenOut = null;
        List<Map<String, Object>> sections = new ArrayList<>();
        List<String> conclusions = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();

        // ---------- 真实取数（只走指标/驾驶舱层） ----------
        @SuppressWarnings("unchecked")
        Map<String, Object> g = cockpit.group();
        List<Map<String, Object>> projectMap = (List<Map<String, Object>>) g.get("projectMap");
        Map<String, Object> proj = projectRow(projectMap, projectId);

        switch (norm) {
            case "SALES" -> {
                BigDecimal signed = dec(proj.get("signedAmount"));
                BigDecimal receipt = dec(proj.get("receiptAmount"));
                BigDecimal rate = signed.signum() == 0 ? null : receipt.multiply(BigDecimal.valueOf(100))
                        .divide(signed, 2, RoundingMode.HALF_UP);
                data.put("signedAmount", signed);
                data.put("receiptAmount", receipt);
                data.put("receiptRate", rate);
                data.put("turnoverRate", proj.get("turnoverRate"));
                section(sections, "签约与回款", Map.of(
                        "signedAmount", signed, "receiptAmount", receipt, "receiptRate", rateOrZero(rate)));
                section(sections, "去化", Map.of("turnoverRate", rateOrZero(proj.get("turnoverRate"))));
                conclusions.add("本月签约额 " + signed + " 元，回款 " + receipt + " 元，回款率 " + rateOrPct(rate) + "，去化率 " + rateOrPct(proj.get("turnoverRate")) + "。");
            }
            case "COST" -> {
                BigDecimal target = dec(proj.get("costTarget"));
                BigDecimal dynamic = dec(proj.get("costDynamic"));
                Object deviation = proj.get("costDeviationRate");
                data.put("targetCost", target);
                data.put("dynamicCost", dynamic);
                data.put("deviationRate", deviation);
                data.put("dynamicProfit", proj.get("dynamicProfit"));
                section(sections, "三算对比", Map.of(
                        "targetCost", target, "dynamicCost", dynamic,
                        "deviationRate", deviationOrZero(deviation), "dynamicProfit", dec(proj.get("dynamicProfit"))));
                String cmp = dynamic.compareTo(target) > 0 ? "超目标" : "低于目标";
                conclusions.add("本月动态成本 " + dynamic + "，目标成本 " + target + "，" + cmp + " " + absPct(deviation) + "，动态利润 " + dec(proj.get("dynamicProfit")) + " 元。");
            }
            case "PROC" -> {
                Map<String, Object> pdata = (Map<String, Object>) cockpit.topic("proc").get("data");
                List<Map<String, Object>> deviation = (List<Map<String, Object>>) pdata.get("deviation");
                List<Map<String, Object>> collusion = (List<Map<String, Object>>) pdata.get("collusionStats");
                data.put("procDeviation", deviation);
                data.put("collusionStats", collusion);
                section(sections, "定标价偏差", Map.of("rows", deviation == null ? List.of() : deviation));
                section(sections, "围串标统计", Map.of("rows", collusion == null ? List.of() : collusion));
                conclusions.add("招采定标价偏差 " + (deviation == null ? 0 : deviation.size()) + " 条，围串标预警 "
                        + (collusion == null ? 0 : collusion.size()) + " 类。");
            }
        }

        // ---------- LLM 解读（可选） ----------
        String aiMode = "RULE";
        String insight = null;
        AiPrompt prompt = findPrompt(promptCodeOf(norm));
        if (prompt != null) {
            Map<String, Object> ctx = new LinkedHashMap<>(data);
            ctx.put("question", title);
            AiLlmResult res = gateway.chat(prompt, ctx);
            if (res.isSuccess() && !guardrail.isLowConfidence(res.getContent())) {
                aiMode = "LLM";
                insight = res.getContent();
                tokenIn = res.getTokenIn();
                tokenOut = res.getTokenOut();
            }
        }
        if (insight != null) {
            section(sections, "解读与建议(LLM)", Map.of("content", insight));
        }

        // ---------- 组装报告 ----------
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("title", title);
        report.put("type", norm);
        report.put("projectId", projectId);
        report.put("month", month);
        report.put("sections", sections);
        report.put("conclusions", conclusions);
        report.put("advisory", guardrail.advisory());
        report.put("aiMode", aiMode);

        String inputJson = safeJson(Map.of("type", norm, "projectId", String.valueOf(projectId), "month", month == null ? "" : month));
        String outputJson = safeJson(report);

        // ---------- 任务中心 + 留痕 ----------
        AiTask task = new AiTask();
        task.setTaskCode("RPT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        task.setType("REPORT");
        task.setStatus("DONE");
        task.setTitle(title);
        task.setInput(inputJson);
        task.setOutput(outputJson);
        taskMapper.insert(task);

        audit.record("REPORT", aiMode, configId, inputJson, outputJson, tokenIn, tokenOut, null,
                System.currentTimeMillis() - t0, "SUCCESS");

        report.put("taskId", task.getId());
        report.put("taskCode", task.getTaskCode());
        return report;
    }

    /**
     * 数字员工模式：调用指定员工的任务技能生成报告。
     * 返回结构与 generate() 兼容（sections + conclusions），额外带 employeeName / skillName / runId。
     */
    public Map<String, Object> generateByEmployee(Long taskId) {
        var task = digTaskMapper.selectById(taskId);
        if (task == null) throw new BizException("任务不存在");
        var emp = digEmployeeMapper.selectById(task.getEmployeeId());
        if (emp == null) throw new BizException("员工不存在");
        var skill = digSkillMapper.selectById(task.getSkillId());
        if (skill == null) throw new BizException("技能不存在");

        // 执行
        Long runId = digitalExecutor.execute(taskId, "MANUAL");

        // 知识库检索
        List<Map<String, Object>> kbRefs = new ArrayList<>();
        try {
            String kw = (skill.getName() == null ? "" : skill.getName()) + " 报告";
            var docs = kbService.searchForContext(kw, null, 3);
            for (var d : docs) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("title", d.getTitle());
                ref.put("category", d.getCategory());
                ref.put("content", d.getContent() != null && d.getContent().length() > 300 ? d.getContent().substring(0, 300) + "…" : d.getContent());
                kbRefs.add(ref);
            }
        } catch (Exception ignore) {}

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", emp.getName() + "·" + task.getName());
        out.put("employeeName", emp.getName());
        out.put("skillName", skill.getName());
        out.put("runId", runId);
        out.put("aiMode", "EMPLOYEE");
        out.put("kbRefs", kbRefs);
        out.put("note", "已触发数字员工执行，runId=" + runId + "，可在任务中心查看过程与结果");
        return out;
    }

    public PageResult<AiTask> list(long current, long size) {
        LambdaQueryWrapper<AiTask> qw = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getType, "REPORT").orderByDesc(AiTask::getId);
        Page<AiTask> p = taskMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public AiTask detail(Long id) {
        AiTask t = taskMapper.selectById(id);
        if (t == null) throw new BizException("报告不存在");
        return t;
    }

    // ==================== 内部 ====================

    private String normalize(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.contains("销售") || t.equalsIgnoreCase("SALES") || t.equalsIgnoreCase("SALES_DAILY")) return "SALES";
        if (t.contains("成本") || t.equalsIgnoreCase("COST") || t.equalsIgnoreCase("COST_MONTHLY")) return "COST";
        if (t.contains("采购") || t.contains("招采") || t.equalsIgnoreCase("PROC") || t.equalsIgnoreCase("PROC_ANALYSIS")) return "PROC";
        return null;
    }

    private String titleOf(String norm) {
        return switch (norm) {
            case "SALES" -> "销售日报";
            case "COST" -> "成本月报";
            default -> "采购分析";
        };
    }

    private String promptCodeOf(String norm) {
        return switch (norm) {
            case "SALES" -> "REPORT_SALES";
            case "COST" -> "REPORT_COST";
            default -> "REPORT_PROC";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectRow(List<Map<String, Object>> projectMap, Long projectId) {
        if (projectId != null) {
            for (Map<String, Object> p : projectMap) {
                if (projectId.equals(toLong(p.get("projectId")))) return p;
            }
            throw new BizException("项目不存在或无指标数据：" + projectId);
        }
        // 集团级：聚合
        Map<String, Object> agg = new LinkedHashMap<>();
        BigDecimal signed = BigDecimal.ZERO, receipt = BigDecimal.ZERO;
        BigDecimal target = BigDecimal.ZERO, dynamic = BigDecimal.ZERO;
        BigDecimal sold = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (Map<String, Object> p : projectMap) {
            signed = signed.add(dec(p.get("signedAmount")));
            receipt = receipt.add(dec(p.get("receiptAmount")));
            target = target.add(dec(p.get("costTarget")));
            dynamic = dynamic.add(dec(p.get("costDynamic")));
        }
        agg.put("signedAmount", signed);
        agg.put("receiptAmount", receipt);
        agg.put("costTarget", target);
        agg.put("costDynamic", dynamic);
        agg.put("costDeviationRate", target.signum() == 0 ? null
                : dynamic.subtract(target).multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP));
        Map<String, Object> cards = (Map<String, Object>) cockpit.group().get("cards");
        agg.put("turnoverRate", null);
        agg.put("dynamicProfit", dec(cards.get("dynamicProfit")));
        return agg;
    }

    private AiPrompt findPrompt(String code) {
        return promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptCode, code).last("LIMIT 1"));
    }

    private void section(List<Map<String, Object>> sections, String name, Map<String, Object> data) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("data", data);
        sections.add(s);
    }

    private String safeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal rateOrZero(Object v) {
        return v == null ? BigDecimal.ZERO : dec(v);
    }

    private static String rateOrPct(Object v) {
        return v == null ? "0%" : dec(v) + "%";
    }

    private static BigDecimal deviationOrZero(Object v) {
        return v == null ? BigDecimal.ZERO : dec(v);
    }

    private static String absPct(Object v) {
        if (v == null) return "0%";
        BigDecimal b = dec(v).abs();
        return b + "%";
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v.toString()).longValue();
        } catch (Exception e) {
            return null;
        }
    }
}
