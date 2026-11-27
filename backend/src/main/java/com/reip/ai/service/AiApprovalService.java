package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.ai.entity.AiPrompt;
import com.reip.ai.mapper.AiBizMapper;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.framework.param.ParamService;
import com.reip.mdm.entity.MdmSupplier;
import com.reip.mdm.mapper.MdmMappers;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批预审（P0）。无 LLM 也能用：规则引擎产出摘要 + 风险 + 相似单据；
 * 有 LLM 时用提示词生成自然语言解读。ai_mode 标记 RULE / LLM。
 * 护栏：AI 意见仅展示，不代批（审批仍走人工 + 流程引擎）。
 */
@Service
@RequiredArgsConstructor
public class AiApprovalService {

    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.MdmLookupMapper mdmLookup;
    private final SaleMappers.ApprovalLookupMapper approvalLookup;
    private final MdmMappers.SupplierMapper supplierMapper;
    private final AiBizMapper bizMapper;
    private final AiMappers.PromptMapper promptMapper;
    private final AiLlmGateway gateway;
    private final AiGuardrail guardrail;
    private final AiAuditService audit;
    private final ParamService paramService;
    private final AiKbService kbService;

    public Map<String, Object> preview(String bizType, Long bizId) {
        if (bizId == null) throw new BizException("缺少单据ID(bizId)");
        String type = bizType == null ? "" : bizType.trim().toUpperCase();

        List<String> summary = new ArrayList<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        List<Map<String, Object>> similar = new ArrayList<>();
        Long configId = gateway.findActive() == null ? null : gateway.findActive().getId();
        long t0 = System.currentTimeMillis();
        Integer tokenIn = null;
        Integer tokenOut = null;

        // ---------- 1. 单据快照（摘要 + 规则风险 + 相似单据，规则引擎，无 LLM 也能出） ----------
        switch (type) {
            case "SALE_CONTRACT" -> buildSaleContract(bizId, summary, risks, similar);
            case "PROC_AWARD" -> buildProcAward(bizId, summary, risks);
            case "EXP_REIMBURSE", "EXP_REIMBURSEMENT" -> buildExpense(bizId, summary, risks);
            case "COST_CHANGE" -> buildCostChange(bizId, summary, risks);
            default -> {
                summary.add("单据类型：" + (bizType == null ? "未知" : bizType));
                summary.add("单据ID：" + bizId);
                summary.add("未识别的业务类型，仅返回基础信息，未做风险检测");
            }
        }

        // ---------- 2. LLM 增强（可选） ----------
        String aiMode = "RULE";
        String llmNote = null;
        String inputDigest = type + "#" + bizId + "\n" + String.join("；", summary)
                + "\n风险：" + risks.stream().map(r -> String.valueOf(r.get("desc"))).reduce((a, b) -> a + "；" + b).orElse("无");
        try {
            AiPrompt prompt = findPrompt("APPROVAL_PREVIEW");
            if (prompt != null) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("bizType", type);
                ctx.put("summary", String.join("；", summary));
                ctx.put("risks", risks.stream().map(r -> r.get("level") + ":" + r.get("desc")).reduce((a, b) -> a + "；" + b).orElse("无"));
                ctx.put("similar", similar.size() + " 条");
                AiLlmResult res = gateway.chat(prompt, ctx);
                if (res.isSuccess() && !guardrail.isLowConfidence(res.getContent())) {
                    aiMode = "LLM";
                    llmNote = res.getContent();
                    tokenIn = res.getTokenIn();
                    tokenOut = res.getTokenOut();
                }
            }
        } catch (Exception ignore) {
            aiMode = "RULE";
        }

        // ---------- 3. 留痕（R3） ----------
        audit.record("APPROVAL_PREVIEW", aiMode, configId,
                inputDigest,
                aiMode + " " + (llmNote == null ? "规则引擎输出" : llmNote),
                tokenIn, tokenOut, null, System.currentTimeMillis() - t0,
                aiMode.equals("LLM") ? "SUCCESS" : "DEGRADED");

        // ---------- 3.5 知识库检索（相关制度文档） ----------
        List<Map<String, Object>> kbRefs = new ArrayList<>();
        String kbKeywords = switch (type) {
            case "SALE_CONTRACT" -> "销售合同 折扣 审批 制度";
            case "PROC_AWARD" -> "招采 定标 供应商 合规";
            case "EXP_REIMBURSE", "EXP_REIMBURSEMENT" -> "费用 报销 审批 制度";
            case "COST_CHANGE" -> "成本 变更 签证 审批";
            default -> type + " 审批 制度";
        };
        try {
            List<com.reip.ai.entity.AiKbDocument> docs = kbService.searchForContext(kbKeywords, null, 3);
            for (var d : docs) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("title", d.getTitle());
                ref.put("category", d.getCategory());
                ref.put("content", d.getContent() != null && d.getContent().length() > 300 ? d.getContent().substring(0, 300) + "…" : d.getContent());
                kbRefs.add(ref);
            }
        } catch (Exception ignore) {}

        // ---------- 4. 组装返回 ----------
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bizType", type);
        out.put("bizId", bizId);
        out.put("summary", summary);
        out.put("risks", risks);
        out.put("similar", similar);
        out.put("aiMode", aiMode);
        out.put("llmNote", llmNote);
        out.put("kbRefs", kbRefs);
        out.put("advisory", guardrail.advisory());
        return out;
    }

    // ==================== SALE_CONTRACT ====================

    private void buildSaleContract(Long id, List<String> summary, List<Map<String, Object>> risks,
                                   List<Map<String, Object>> similar) {
        SaleContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException("销售合同不存在");
        String customer = mdmLookup.selectCustomerName(c.getCustomerId());
        String room = mdmLookup.selectRoomCode(c.getRoomId());
        summary.add("销售合同 " + c.getContractNo() + "（" + (c.getStatus() == null ? "" : c.getStatus()) + "）");
        summary.add("成交总价 " + nvl(c.getContractPrice()) + " 元"
                + (c.getDiscountRate() != null ? "，折扣率 " + c.getDiscountRate() : "")
                + (c.getPayMethod() != null ? "，付款方式 " + c.getPayMethod() : ""));
        summary.add("客户：" + (customer == null ? "未知" : customer)
                + "；房源：" + (room == null ? "未知" : room)
                + (c.getSupplierName() != null ? "；中介：" + c.getSupplierName() : ""));

        // 风险1：折扣低于置业顾问权限上限
        BigDecimal agentMax = approvalLookup.selectAgentMaxRate();
        if (agentMax != null && c.getDiscountRate() != null && c.getDiscountRate().compareTo(agentMax) < 0) {
            risks.add(risk("MEDIUM", "成交折扣低于置业顾问权限上限",
                    "成交折扣率 " + c.getDiscountRate() + " 低于顾问权限下限 " + agentMax + "（依据 sale_discount.AGENT 级 max_rate）"));
        }
        // 风险2/3：供应商黑名单 / 资质过期
        if (c.getSupplierId() != null) {
            MdmSupplier sup = supplierMapper.selectById(c.getSupplierId());
            if (sup != null && "BLACKLIST".equals(sup.getLibType())) {
                risks.add(risk("HIGH", "中介/供应商处于黑名单",
                        "供应商 " + sup.getName() + " 库别=BLACKLIST（依据 mdm_supplier.lib_type）"));
            }
            long expired = bizMapper.countExpiredQual(c.getSupplierId());
            if (expired > 0) {
                risks.add(risk("MEDIUM", "供应商存在已过期资质证照",
                        "供应商 " + (c.getSupplierName() == null ? c.getSupplierId() : c.getSupplierName())
                                + " 有 " + expired + " 项资质已过期（依据 mdm_supplier_qualification.expire_date）"));
            }
        }
        similar.addAll(bizMapper.similarContracts(c.getProjectId(), id, 5));
    }

    // ==================== PROC_AWARD ====================

    private void buildProcAward(Long id, List<String> summary, List<Map<String, Object>> risks) {
        Map<String, Object> a = bizMapper.procAwardById(id);
        if (a == null) throw new BizException("定标单不存在");
        summary.add("定标单 " + a.get("title"));
        summary.add("定标金额 " + nvl(a.get("awardAmount")) + " 元"
                + (a.get("budgetAmount") != null ? "，立项预算 " + a.get("budgetAmount") + " 元" : ""));
        summary.add("中标供应商：" + (a.get("supplierName") == null ? "未知" : a.get("supplierName")));

        Long supplierId = toLong(a.get("supplierId"));
        if (supplierId != null) {
            MdmSupplier sup = supplierMapper.selectById(supplierId);
            if (sup != null && "BLACKLIST".equals(sup.getLibType())) {
                risks.add(risk("HIGH", "中标供应商处于黑名单",
                        "供应商 " + sup.getName() + " 库别=BLACKLIST（依据 mdm_supplier.lib_type）"));
            }
            long expired = bizMapper.countExpiredQual(supplierId);
            if (expired > 0) {
                risks.add(risk("MEDIUM", "中标供应商存在已过期资质",
                        "供应商有 " + expired + " 项资质已过期（依据 mdm_supplier_qualification.expire_date）"));
            }
        }
        BigDecimal award = toDecimal(a.get("awardAmount"));
        BigDecimal budget = toDecimal(a.get("budgetAmount"));
        if (award != null && budget != null && budget.signum() > 0 && award.compareTo(budget) > 0) {
            risks.add(risk("MEDIUM", "定标价高于立项预算",
                    "定标价 " + award + " 高于立项预算 " + budget + "（依据 proc_award/proc_project 金额对比）"));
        }
    }

    // ==================== EXP_REIMBURSE ====================

    private void buildExpense(Long id, List<String> summary, List<Map<String, Object>> risks) {
        Map<String, Object> r = bizMapper.expReimburseById(id);
        if (r == null) throw new BizException("报销单不存在");
        summary.add("报销单 " + r.get("reimbNo") + "（" + r.get("type") + "）");
        summary.add("报销总额 " + nvl(r.get("totalAmount")) + " 元");
        summary.add("报销人：" + (r.get("userName") == null ? "未知" : r.get("userName")));
        BigDecimal over = toDecimal(r.get("overAmount"));
        if (over != null && over.signum() > 0) {
            risks.add(risk("MEDIUM", "报销金额超标",
                    "超标额 " + over + " 元" + (r.get("overReason") != null ? "，原因：" + r.get("overReason") : "")
                            + "（依据 exp_reimburse.over_amount）"));
        }
    }

    // ==================== COST_CHANGE ====================

    private void buildCostChange(Long id, List<String> summary, List<Map<String, Object>> risks) {
        Map<String, Object> c = bizMapper.costChangeById(id);
        if (c == null) throw new BizException("成本变更单不存在");
        summary.add("成本变更单 " + c.get("changeCode") + "：" + c.get("title"));
        summary.add("确认变更金额 " + nvl(c.get("confirmedAmount")) + " 元");
        summary.add("关联合同已签金额 " + nvl(c.get("contractedAmount")) + " 元");
        BigDecimal confirmed = toDecimal(c.get("confirmedAmount"));
        BigDecimal contracted = toDecimal(c.get("contractedAmount"));
        if (confirmed != null && contracted != null && contracted.signum() > 0) {
            BigDecimal rate = confirmed.multiply(BigDecimal.valueOf(100)).divide(contracted, 2, RoundingMode.HALF_UP);
            BigDecimal threshold = paramService.getDecimal("cost.change.warn_rate", new BigDecimal("5"));
            if (rate.compareTo(threshold) > 0) {
                risks.add(risk(rate.compareTo(threshold.multiply(BigDecimal.valueOf(2))) > 0 ? "HIGH" : "MEDIUM",
                        "变更率异常（超预警阈值）",
                        "确认变更/合同额=" + rate + "%，超预警阈值 " + threshold + "%（依据 cost_change/cost_contract 与 sys_param cost.change.warn_rate）"));
            }
        }
    }

    // ==================== 工具 ====================

    private AiPrompt findPrompt(String code) {
        return promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptCode, code).last("LIMIT 1"));
    }

    private Map<String, Object> risk(String level, String desc, String evidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("desc", desc);
        m.put("evidence", evidence);
        return m;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
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

    private static String nvl(Object v) {
        return v == null ? "0" : v.toString();
    }
}
