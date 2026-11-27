package com.reip.digital.service;

import com.reip.digital.mapper.DigitalBizMapper;
import com.reip.portal.service.CockpitV2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数字员工取数器：按技能取数域采集真实业务数据（只读）。
 * 返回 Map 同时包含：展示用指标 + 条件触发用数值键（pendingAward/overrunProjects/lowTurnoverProjects 等）。
 * 取数失败不抛错，返回带 note 的空数据（降级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalDataFetcher {

    private final CockpitV2Service cockpit;
    private final DigitalBizMapper biz;

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetch(String dataSource) {
        String ds = dataSource == null ? "NONE" : dataSource.toUpperCase();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            switch (ds) {
                case "PROC" -> {
                    long pendingAward = safeLong(biz.countPendingAward());
                    long openProjects = safeLong(biz.countOpenProcProject());
                    int deviationCount = 0;
                    int collusionCount = 0;
                    try {
                        Map<String, Object> topicData = (Map<String, Object>) cockpit.topic("proc").get("data");
                        deviationCount = sizeOf(topicData.get("deviation"));
                        collusionCount = sizeOf(topicData.get("collusionStats"));
                    } catch (Exception ignore) {
                        // 专题数据缺失不影响核心指标
                    }
                    data.put("pendingAward", pendingAward);
                    data.put("openProjects", openProjects);
                    data.put("deviationCount", deviationCount);
                    data.put("collusionCount", collusionCount);
                }
                case "COST", "FIN", "SAL" -> {
                    Map<String, Object> group = cockpit.group();
                    Map<String, Object> cards = (Map<String, Object>) group.getOrDefault("cards", Map.of());
                    List<Map<String, Object>> projects = (List<Map<String, Object>>) group.getOrDefault("projectMap", List.of());
                    BigDecimal signed = dec(cards.get("signedAmount"));
                    BigDecimal receipt = dec(cards.get("receiptAmount"));
                    BigDecimal dynamicProfit = dec(cards.get("dynamicProfit"));
                    BigDecimal receiptRate = signed.signum() == 0 ? BigDecimal.ZERO
                            : receipt.multiply(BigDecimal.valueOf(100)).divide(signed, 2, RoundingMode.HALF_UP);

                    // 逐项目指标
                    long overrunProjects = 0;
                    long lowTurnoverProjects = 0;
                    long hasTurnover = 0;
                    BigDecimal turnoverSum = BigDecimal.ZERO;
                    BigDecimal costTarget = BigDecimal.ZERO;
                    BigDecimal costDynamic = BigDecimal.ZERO;
                    for (Map<String, Object> p : projects) {
                        BigDecimal t = dec(p.get("costTarget"));
                        BigDecimal d = dec(p.get("costDynamic"));
                        costTarget = costTarget.add(t);
                        costDynamic = costDynamic.add(d);
                        if (t.signum() > 0 && d.compareTo(t) > 0) overrunProjects++;
                        BigDecimal tr = p.get("turnoverRate") == null ? null : dec(p.get("turnoverRate"));
                        if (tr != null && tr.signum() > 0) {
                            hasTurnover++;
                            turnoverSum = turnoverSum.add(tr);
                            if (tr.compareTo(new BigDecimal("0.25")) < 0) lowTurnoverProjects++;
                        }
                    }
                    BigDecimal avgTurnover = hasTurnover == 0 ? BigDecimal.ZERO
                            : turnoverSum.divide(BigDecimal.valueOf(hasTurnover), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal devRate = costTarget.signum() == 0 ? BigDecimal.ZERO
                            : costDynamic.subtract(costTarget).multiply(BigDecimal.valueOf(100))
                            .divide(costTarget, 2, RoundingMode.HALF_UP);

                    data.put("signedAmount", signed);
                    data.put("receiptAmount", receipt);
                    data.put("receiptRate", receiptRate);
                    data.put("dynamicProfit", dynamicProfit);
                    data.put("projectCount", projects.size());
                    if (ds.equals("COST")) {
                        data.put("costTarget", costTarget);
                        data.put("costDynamic", costDynamic);
                        data.put("costDeviationRate", devRate);
                        data.put("overrunProjects", overrunProjects);
                    }
                    if (ds.equals("FIN")) {
                        data.put("costTarget", costTarget);
                        data.put("costDynamic", costDynamic);
                    }
                    if (ds.equals("SAL")) {
                        data.put("avgTurnoverRate", avgTurnover);
                        data.put("lowTurnoverProjects", lowTurnoverProjects);
                    }
                }
                default -> {
                    data.put("today", LocalDate.now().toString());
                    data.put("scope", "通用技能（不采集业务数据）");
                }
            }
        } catch (Exception e) {
            log.warn("[digital] 取数失败 ds={} err={}", ds, e.getMessage());
            data.clear();
            data.put("note", "业务数据采集失败：" + e.getMessage());
        }
        return data;
    }

    /** 条件指标取值（供条件触发判断） */
    public Object metric(Map<String, Object> data, String key) {
        if (data == null || key == null) return null;
        return data.get(key);
    }

    /**
     * 条件触发可用指标（按取数域），供前端任务表单按技能取数域过滤。
     * 与 fetch() 实际产出的数值键一一对应，保证条件表达式可求值。
     */
    public Map<String, List<Map<String, String>>> condMetrics() {
        Map<String, List<Map<String, String>>> m = new LinkedHashMap<>();
        m.put("PROC", List.of(
                kv("pendingAward", "待审批定标数"),
                kv("openProjects", "在办招采项目数"),
                kv("deviationCount", "定标价偏差条数"),
                kv("collusionCount", "围串标预警数")));
        m.put("COST", List.of(
                kv("costTarget", "目标成本"),
                kv("costDynamic", "动态成本"),
                kv("costDeviationRate", "成本偏差率(%)"),
                kv("overrunProjects", "成本超支项目数"),
                kv("signedAmount", "签约额"),
                kv("receiptRate", "回款率(%)")));
        m.put("FIN", List.of(
                kv("signedAmount", "签约额"),
                kv("receiptAmount", "实收回款额"),
                kv("receiptRate", "回款率(%)"),
                kv("dynamicProfit", "动态利润"),
                kv("costTarget", "目标成本"),
                kv("costDynamic", "动态成本")));
        m.put("SAL", List.of(
                kv("signedAmount", "签约额"),
                kv("receiptAmount", "实收回款额"),
                kv("receiptRate", "回款率(%)"),
                kv("avgTurnoverRate", "平均去化率(%)"),
                kv("lowTurnoverProjects", "去化偏低项目数"),
                kv("projectCount", "项目总数")));
        m.put("NONE", List.of());
        return m;
    }

    private Map<String, String> kv(String key, String label) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("key", key);
        v.put("label", label);
        return v;
    }

    private int sizeOf(Object o) {
        if (o instanceof List<?> l) return l.size();
        if (o instanceof Map<?, ?> m) return m.size();
        return 0;
    }

    private long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
