package com.reip.invest.service;

import com.reip.invest.mapper.InvestMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资总览 / 投资驾驶舱（组合只读聚合，无新表）
 * 数据源：inv_calc_version 基线（优先 LOCKED）/ inv_dynamic_profit 最新动态 /
 * inv_value 业态货值 / inv_value_flow 供销存 / mdm_project 状态与城市
 */
@Service
@RequiredArgsConstructor
public class InvestOverviewService {

    private final InvestMappers.OverviewMapper m;

    /** 投资总览：KPI + 项目投资清单 + 状态 / 城市分布 */
    public Map<String, Object> overview() {
        BaseData d = load();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kpi", d.kpi());
        r.put("projects", d.rows);
        r.put("statusDist", toDist(d.statusDist));
        r.put("cityValue", toDist(d.cityValue));
        return r;
    }

    /** 投资驾驶舱：KPI 卡 + 基线 vs 动态对比 + IRR 排名 + 趋势 + 业态构成 + 预警 */
    public Map<String, Object> cockpit() {
        BaseData d = load();

        Map<String, Object> cards = d.kpi();
        cards.put("avgIrr", avgBaseline(d, "irr"));
        cards.put("avgPayback", avgBaseline(d, "paybackYears"));
        cards.put("shortfallCount", d.shortfallCount);

        List<Map<String, Object>> profitRank = new ArrayList<>();
        List<Map<String, Object>> irrRank = new ArrayList<>();
        for (Map<String, Object> row : d.rows) {
            Map<String, Object> base = (Map<String, Object>) row.get("baseline");
            Map<String, Object> dyn = (Map<String, Object>) row.get("dynamic");
            if (base == null && dyn == null) continue;

            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("projectName", row.get("projectName"));
            pr.put("baseline", base != null ? base.get("netProfit") : null);
            pr.put("dynamic", dyn != null ? dyn.get("dynamicProfit") : null);
            profitRank.add(pr);

            if (base != null) {
                Map<String, Object> ir = new LinkedHashMap<>();
                ir.put("projectName", row.get("projectName"));
                ir.put("irr", base.get("irr"));
                ir.put("paybackYears", base.get("paybackYears"));
                ir.put("profitRate", base.get("profitRate"));
                irrRank.add(ir);
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cards", cards);
        r.put("profitRank", profitRank);
        r.put("irrRank", irrRank);
        r.put("profitTrend", m.selectProfitTrend());
        r.put("categoryValue", m.selectCategoryValue());
        r.put("flowTrend", m.selectFlowTrend());
        r.put("alerts", buildAlerts(d));
        return r;
    }

    private BaseData load() {
        BaseData d = new BaseData();
        Map<Long, Map<String, Object>> baseline = byProject(m.selectBaselinePerProject());
        Map<Long, Map<String, Object>> dynamic = byProject(m.selectLatestDynamicPerProject());

        for (Map<String, Object> p : m.selectProjects()) {
            Long pid = id(p.get("id"));
            d.statusDist.merge(str(p.get("status")), 1, Integer::sum);

            Map<String, Object> base = baseline.get(pid);
            Map<String, Object> dyn = dynamic.get(pid);
            if (base != null) {
                d.baselines.add(base);
                String city = str(p.get("city"));
                if (!city.isEmpty()) {
                    d.cityValue.merge(city, num(base.get("saleValue")), BigDecimal::add);
                }
            }

            BigDecimal diff = null;
            if (base != null && dyn != null
                    && base.get("netProfit") != null && dyn.get("dynamicProfit") != null) {
                diff = num(dyn.get("dynamicProfit")).subtract(num(base.get("netProfit")));
                if (diff.signum() < 0) d.shortfallCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", pid);
            row.put("projectName", p.get("name"));
            row.put("city", p.get("city"));
            row.put("status", p.get("status"));
            row.put("baseline", base);
            row.put("dynamic", dyn);
            row.put("profitDiff", diff);
            d.rows.add(row);
        }
        return d;
    }

    /** 预警：缺基线（中）/ 动态利润低于拿地基线（高） */
    private List<Map<String, Object>> buildAlerts(BaseData d) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> row : d.rows) {
            Map<String, Object> base = (Map<String, Object>) row.get("baseline");
            if (base == null) {
                alerts.add(alert("NO_BASELINE", "MID", "缺基线",
                        "项目 " + row.get("projectName") + " 尚未建立投资测算基线（锁定版本）", "/invest/calc"));
                continue;
            }
            BigDecimal diff = (BigDecimal) row.get("profitDiff");
            if (diff != null && diff.signum() < 0) {
                alerts.add(alert("SHORTFALL", "HIGH", "利润缺口",
                        "项目 " + row.get("projectName") + " 动态利润低于拿地基线 " + wan(diff.abs()) + " 万", "/invest/profit"));
            }
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

    private Map<Long, Map<String, Object>> byProject(List<Map<String, Object>> list) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> r : list) {
            map.put(id(r.get("projectId")), r);
        }
        return map;
    }

    /** 基线均值（IRR / 回收期，忽略空值） */
    private BigDecimal avgBaseline(BaseData d, String key) {
        BigDecimal sum = null;
        int cnt = 0;
        for (Map<String, Object> b : d.baselines) {
            Object v = b.get(key);
            if (v == null) continue;
            sum = (sum == null ? BigDecimal.ZERO : sum).add(new BigDecimal(v.toString()));
            cnt++;
        }
        return cnt == 0 ? null : sum.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> toDist(Map<String, ?> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : src.entrySet()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", e.getKey());
            r.put("value", e.getValue());
            out.add(r);
        }
        return out;
    }

    private static BigDecimal num(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    private Long id(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private String wan(BigDecimal v) {
        return v.divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).toPlainString() + " 万";
    }

    /** 一次查询后的组合数据（总览 / 驾驶舱共用） */
    private static class BaseData {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> baselines = new ArrayList<>();
        int shortfallCount = 0;
        Map<String, Integer> statusDist = new LinkedHashMap<>();
        Map<String, BigDecimal> cityValue = new LinkedHashMap<>();

        Map<String, Object> kpi() {
            BigDecimal totalInvestment = BigDecimal.ZERO;
            BigDecimal totalSaleValue = BigDecimal.ZERO;
            BigDecimal totalNetProfit = BigDecimal.ZERO;
            BigDecimal totalDynamicProfit = BigDecimal.ZERO;
            int investedCount = 0;
            boolean hasDynamic = false;
            for (Map<String, Object> row : rows) {
                Map<String, Object> base = (Map<String, Object>) row.get("baseline");
                if (base != null) {
                    investedCount++;
                    totalInvestment = totalInvestment.add(num(base.get("totalCost")));
                    totalSaleValue = totalSaleValue.add(num(base.get("saleValue")));
                    totalNetProfit = totalNetProfit.add(num(base.get("netProfit")));
                }
                Map<String, Object> dyn = (Map<String, Object>) row.get("dynamic");
                if (dyn != null) {
                    hasDynamic = true;
                    totalDynamicProfit = totalDynamicProfit.add(num(dyn.get("dynamicProfit")));
                }
            }
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("projectCount", rows.size());
            k.put("investedCount", investedCount);
            k.put("totalInvestment", totalInvestment);
            k.put("totalSaleValue", totalSaleValue);
            k.put("totalNetProfit", totalNetProfit);
            k.put("avgProfitRate", rate(totalNetProfit, totalSaleValue));
            k.put("totalDynamicProfit", totalDynamicProfit);
            k.put("profitDiff", hasDynamic ? totalDynamicProfit.subtract(totalNetProfit) : null);
            return k;
        }
    }

    private static BigDecimal rate(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
