package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.ai.entity.AiMetricSemantic;
import com.reip.ai.entity.AiPrompt;
import com.reip.ai.mapper.AiMappers;
import com.reip.portal.entity.CockpitMetricDef;
import com.reip.portal.mapper.ReportMetricMapper;
import com.reip.portal.service.CockpitV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标问答（R1：只走语义层 + 指标/下钻接口，禁止裸 SQL；无数据明确说不知道）。
 * 无 LLM 也能用：关键词匹配语义层 → 命中指标 → 调 CockpitV2Service 取真实值。
 */
@Service
@RequiredArgsConstructor
public class AiQueryService {

    private final AiMappers.MetricSemanticMapper semanticMapper;
    private final AiMappers.PromptMapper promptMapper;
    private final ReportMetricMapper metricDefMapper;
    private final CockpitV2Service cockpit;
    private final AiLlmGateway gateway;
    private final AiGuardrail guardrail;
    private final AiAuditService audit;

    public Map<String, Object> ask(String question, Long projectId) {
        if (question == null || question.isBlank()) throw new com.reip.common.exception.BizException("请输入问题");
        String q = question.trim();
        Long configId = gateway.findActive() == null ? null : gateway.findActive().getId();
        long t0 = System.currentTimeMillis();

        // ---------- 1. 规则引擎：关键词 → 指标 ----------
        AiMetricSemantic hit = matchMetric(q);
        String aiMode = "RULE";
        String llmNote = null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", q);

        if (hit == null) {
            // 规则未命中 → 尝试 LLM 解析（可选）
            String llmCode = tryLlmParse(q, configId);
            if (llmCode != null) {
                hit = semanticMapper.selectOne(new LambdaQueryWrapper<AiMetricSemantic>()
                        .eq(AiMetricSemantic::getMetricCode, llmCode).last("LIMIT 1"));
                aiMode = "LLM";
            }
        }

        if (hit == null) {
            out.put("matched", false);
            out.put("answer", "未找到对应指标。请尝试：签约额/回款率/去化率/动态成本/动态利润/费效比。");
            out.put("aiMode", aiMode);
            audit.record("QUERY", aiMode, configId, q, "未找到对应指标", null, null, null, System.currentTimeMillis() - t0, "DEGRADED");
            return out;
        }

        // ---------- 2. 命中指标 → 取真实值 + 口径 + 来源 ----------
        Map<String, Object> value = fetchValue(hit.getMetricCode(), projectId);
        CockpitMetricDef def = activeDef(hit.getMetricCode());

        String answer = hit.getMetricName() + "：" + value.get("value")
                + (value.get("unit") != null ? value.get("unit") : "")
                + "。口径：" + (def == null ? "无口径定义" : def.getFormula())
                + "。来源：" + (def == null ? "驾驶舱指标层" : def.getSourceTable());

        out.put("matched", true);
        out.put("metricCode", hit.getMetricCode());
        out.put("metricName", hit.getMetricName());
        out.put("value", value.get("value"));
        out.put("unit", value.get("unit"));
        out.put("detail", value.get("detail"));
        out.put("caliber", def == null ? null : def.getFormula());
        out.put("source", def == null ? null : def.getSourceTable());
        out.put("answer", answer);
        out.put("aiMode", aiMode);
        out.put("llmNote", llmNote);
        audit.record("QUERY", aiMode, configId, q, answer, null, null, null, System.currentTimeMillis() - t0, "SUCCESS");
        return out;
    }

    // ==================== 规则：关键词匹配 ====================

    private AiMetricSemantic matchMetric(String q) {
        List<AiMetricSemantic> all = semanticMapper.selectList(new LambdaQueryWrapper<AiMetricSemantic>()
                .orderByAsc(AiMetricSemantic::getId));
        AiMetricSemantic best = null;
        int bestScore = 0;
        for (AiMetricSemantic s : all) {
            int score = 0;
            if (s.getKeywords() != null) {
                for (String kw : s.getKeywords().split("[,，]")) {
                    String k = kw.trim();
                    if (k.length() >= 2 && q.contains(k)) score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** LLM 解析（可选）：从候选指标中挑一个编码 */
    private String tryLlmParse(String q, Long configId) {
        AiPrompt prompt = promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptCode, "QUERY").last("LIMIT 1"));
        if (prompt == null) return null;
        List<String> codes = semanticMapper.selectList(null).stream().map(AiMetricSemantic::getMetricCode).toList();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("question", q);
        ctx.put("candidates", codes.toString());
        AiLlmResult res = gateway.chat(prompt, ctx);
        if (!res.isSuccess()) return null;
        String content = res.getContent();
        for (String code : codes) {
            if (content != null && content.toUpperCase().contains(code)) return code;
        }
        return null;
    }

    // ==================== 取真实值（只走指标/驾驶舱层） ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchValue(String code, Long projectId) {
        Map<String, Object> g = cockpit.group();
        Map<String, Object> cards = (Map<String, Object>) g.get("cards");
        List<Map<String, Object>> projectMap = (List<Map<String, Object>>) g.get("projectMap");
        Map<String, Object> v = new LinkedHashMap<>();
        switch (code) {
            case "SIGN_AMOUNT" -> { v.put("value", cards.get("signedAmount")); v.put("unit", "元"); }
            case "RECEIPT_AMOUNT" -> { v.put("value", cards.get("receiptAmount")); v.put("unit", "元"); }
            case "RECEIPT_RATE" -> { v.put("value", cards.get("receiptRate")); v.put("unit", "%"); }
            case "DYNAMIC_COST" -> { v.put("value", cards.get("dynamicCost")); v.put("unit", "元"); }
            case "DYNAMIC_PROFIT" -> { v.put("value", cards.get("dynamicProfit")); v.put("unit", "元"); }
            case "TURNOVER_RATE" -> v.putAll(turnoverRate(projectMap, projectId));
            case "COST_EFFICIENCY" -> {
                Map<String, Object> data = (Map<String, Object>) cockpit.topic("expense").get("data");
                Map<String, Object> fee = (Map<String, Object>) data.get("threeFee");
                v.put("value", fee.get("total"));
                v.put("unit", "元");
                v.put("detail", fee);
            }
            default -> { v.put("value", null); v.put("unit", ""); }
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> turnoverRate(List<Map<String, Object>> projectMap, Long projectId) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (projectId != null) {
            for (Map<String, Object> p : projectMap) {
                if (projectId.equals(toLong(p.get("projectId")))) {
                    v.put("value", p.get("turnoverRate"));
                    v.put("unit", "%");
                    return v;
                }
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (Map<String, Object> p : projectMap) {
            Object tr = p.get("turnoverRate");
            if (tr == null) continue;
            sum = sum.add(toDecimal(tr));
            n++;
        }
        v.put("value", n == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
        v.put("unit", "%");
        return v;
    }

    private CockpitMetricDef activeDef(String code) {
        return metricDefMapper.selectOne(new LambdaQueryWrapper<CockpitMetricDef>()
                .eq(CockpitMetricDef::getMetricCode, code)
                .eq(CockpitMetricDef::getStatus, "ACTIVE")
                .orderByDesc(CockpitMetricDef::getId)
                .last("LIMIT 1"));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
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
