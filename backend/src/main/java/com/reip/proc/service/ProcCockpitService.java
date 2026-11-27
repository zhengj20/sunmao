package com.reip.proc.service;

import com.reip.proc.mapper.ProcMappers.CockpitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招采驾驶舱：KPI 指标总览 + 统计分析（方式/阶段/周期/偏差/供应商）+ 预警（围串标/超预算/超期）
 */
@Service
@RequiredArgsConstructor
public class ProcCockpitService {

    /** 超期阈值（天）：立项进行中且停滞超过该天数视为超期 */
    private static final int OVERDUE_DAYS = 30;

    private final CockpitMapper m;

    public Map<String, Object> cockpit() {
        Map<String, Object> base = m.selectBaseCards();
        Map<String, Object> award = m.selectAwardCards();

        long projectCount = num(base.get("projectCount"));
        long publicBid = num(base.get("publicBidCount"));
        long direct = num(base.get("directCount"));

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("projectCount", projectCount);
        cards.put("inProgressCount", num(base.get("inProgressCount")));
        cards.put("awardedCount", num(base.get("awardedCount")));
        cards.put("pendingCount", num(base.get("pendingCount")));
        cards.put("budgetTotal", base.get("budgetTotal"));
        cards.put("awardedTotal", award.get("awardedTotal"));
        cards.put("avgCycleDays", award.get("avgCycleDays"));
        cards.put("avgDeviationPct", round(award.get("avgDeviationPct")));
        cards.put("publicBidPct", projectCount > 0 ? round(publicBid * 100.0 / projectCount) : 0d);
        cards.put("directPct", projectCount > 0 ? round(direct * 100.0 / projectCount) : 0d);
        cards.put("overdueCount", m.selectOverdueCount(OVERDUE_DAYS));
        cards.put("collusionCount", m.selectCollusionCount());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cards", cards);
        r.put("methodDist", m.selectMethodDist());
        r.put("stageDist", m.selectStageDist());
        r.put("cycleTrend", m.selectCycleTrend());
        r.put("deviation", m.selectDeviation());
        r.put("supplierTop", m.selectSupplierTop());
        r.put("alerts", buildAlerts());
        return r;
    }

    private List<Map<String, Object>> buildAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();

        for (Map<String, Object> w : m.selectCollusionList()) {
            alerts.add(alert("COLLUSION", "HIGH", "围串标预警", str(w.get("detail")), "/proc/overview"));
        }
        for (Map<String, Object> o : m.selectBudgetOverrun()) {
            alerts.add(alert("BUDGET", "MID", "定标超预算",
                    str(o.get("name")) + " 定标超预算 " + round(num(o.get("deviationPct"))) + "%", "/proc/overview"));
        }
        for (Map<String, Object> o : m.selectOverdueList(OVERDUE_DAYS)) {
            alerts.add(alert("OVERDUE", "HIGH", "流程超期",
                    str(o.get("title")) + " 停滞超 " + OVERDUE_DAYS + " 天", "/proc/overview"));
        }
        return alerts;
    }

    private Map<String, Object> alert(String type, String level, String title, String desc, String link) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        a.put("level", level);
        a.put("title", title);
        a.put("desc", desc);
        a.put("link", link);
        return a;
    }

    private long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private double round(Object v) {
        double d = v == null ? 0d : ((Number) v).doubleValue();
        return Math.round(d * 100.0) / 100.0;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
