package com.reip.portal.service;

import com.reip.common.exception.BizException;
import com.reip.portal.mapper.CockpitV2Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三级驾驶舱 + 专题分析 + 下钻（全部跨域真实取数，替换原 30% 演示值）
 */
@Service
@RequiredArgsConstructor
public class CockpitV2Service {

    private static final BigDecimal GREEN_RATE = new BigDecimal("0.02");
    private static final BigDecimal YELLOW_RATE = new BigDecimal("0.05");
    private static final BigDecimal SELL_GREEN = new BigDecimal("0.50");
    private static final BigDecimal SELL_YELLOW = new BigDecimal("0.25");

    private final CockpitV2Mapper mapper;

    // ==================== 集团驾驶舱 ====================

    public Map<String, Object> group() {
        List<Map<String, Object>> projects = mapper.selectProjectMetrics(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cards", groupCards());
        r.put("projectMap", projectMap(projects));
        r.put("regionRanking", regionRanking(projects));
        r.put("investmentRealization", investmentRealization(projects));
        r.put("riskAlerts", riskAlerts(projects));
        return r;
    }

    private Map<String, Object> groupCards() {
        Map<String, Object> c = new LinkedHashMap<>();
        BigDecimal signed = mapper.sumSignedAmount();
        BigDecimal receipt = mapper.sumReceiptAmount();
        c.put("signedAmount", signed);
        c.put("receiptAmount", receipt);
        c.put("supplyValue", mapper.sumSupplyValue());
        c.put("totalValue", mapper.sumTotalValue());
        c.put("dynamicCost", mapper.sumLatestDynamicCost());
        c.put("dynamicProfit", mapper.sumLatestDynamicProfit());
        c.put("receiptRate", ratio(receipt, signed)); // 回款率=实收/签约
        return c;
    }

    private List<Map<String, Object>> projectMap(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("projectId", row.get("projectId"));
            p.put("projectName", row.get("projectName"));
            p.put("orgName", orDefault(row.get("orgName"), "未分配"));
            p.put("city", row.get("city"));
            BigDecimal signed = dec(row.get("signedAmount"));
            BigDecimal receipt = dec(row.get("receiptAmount"));
            BigDecimal sold = dec(row.get("soldValue"));
            BigDecimal total = dec(row.get("totalRoomValue"));
            BigDecimal costTarget = dec(row.get("costTarget"));
            BigDecimal costDynamic = dec(row.get("costDynamic"));
            BigDecimal supply = dec(row.get("supplyValue"));

            p.put("signedAmount", signed);
            p.put("receiptAmount", receipt);
            p.put("supplyValue", supply);
            p.put("turnoverRate", ratio(sold, total));
            p.put("costTarget", costTarget);
            p.put("costDynamic", costDynamic);
            p.put("costDeviation", costDynamic.subtract(costTarget));
            p.put("costDeviationRate", costTarget.signum() == 0 ? null
                    : costDynamic.subtract(costTarget).divide(costTarget, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

            long planRed = lng(row.get("planRed"));
            long planYellow = lng(row.get("planYellow"));
            String planLight = planTotal(row) == 0 ? null : (planRed > 0 ? "RED" : planYellow > 0 ? "YELLOW" : "GREEN");
            String costLight = costTarget.signum() > 0 ? costLightOf(costDynamic, costTarget) : null;
            String salesLight = total.signum() > 0 ? salesLightOf(sold, total) : null;

            p.put("planLight", planLight);
            p.put("costLight", costLight);
            p.put("salesLight", salesLight);
            p.put("light", worst(planLight, costLight, salesLight));
            p.put("dynamicProfit", dec(row.get("dynamicProfit")));
            out.add(p);
        }
        return out;
    }

    private List<Map<String, Object>> regionRanking(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> byOrg = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String org = orDefault(row.get("orgName"), "未分配");
            Map<String, Object> agg = byOrg.computeIfAbsent(org, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orgName", k);
                m.put("projectCount", 0L);
                m.put("signedAmount", BigDecimal.ZERO);
                m.put("receiptAmount", BigDecimal.ZERO);
                m.put("dynamicProfit", BigDecimal.ZERO);
                m.put("costDeviation", BigDecimal.ZERO);
                return m;
            });
            agg.put("projectCount", (long) agg.get("projectCount") + 1);
            agg.put("signedAmount", dec(agg.get("signedAmount")).add(dec(row.get("signedAmount"))));
            agg.put("receiptAmount", dec(agg.get("receiptAmount")).add(dec(row.get("receiptAmount"))));
            agg.put("dynamicProfit", dec(agg.get("dynamicProfit")).add(dec(row.get("dynamicProfit"))));
            agg.put("costDeviation", dec(agg.get("costDeviation"))
                    .add(dec(row.get("costDynamic")).subtract(dec(row.get("costTarget")))));
        }
        List<Map<String, Object>> list = new ArrayList<>(byOrg.values());
        list.sort(Comparator.comparing(m -> dec(((Map<?, ?>) m).get("dynamicProfit"))).reversed());
        int rank = 1;
        for (Map<String, Object> m : list) m.put("rank", rank++);
        return list;
    }

    private List<Map<String, Object>> investmentRealization(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal dynamic = dec(row.get("dynamicProfit"));
            BigDecimal baseline = dec(row.get("baselineProfit"));
            if (baseline.signum() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", row.get("projectId"));
            m.put("projectName", row.get("projectName"));
            m.put("dynamicProfit", dynamic);
            m.put("baselineProfit", baseline);
            m.put("realizationPct", dynamic.multiply(BigDecimal.valueOf(100))
                    .divide(baseline, 2, RoundingMode.HALF_UP));
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> dec(((Map<?, ?>) m).get("realizationPct"))).reversed());
        return out;
    }

    private List<Map<String, Object>> riskAlerts(List<Map<String, Object>> rows) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        // 1) 计划红灯里程碑
        for (Map<String, Object> r : mapper.selectRedMilestones()) {
            alerts.add(alert("PLAN", "HIGH", r.get("projectId"), String.valueOf(r.get("taskName")),
                    "计划红灯里程碑", "/plan"));
        }
        // 2) 成本超支（动态成本 > 目标成本 且偏差>5%）
        for (Map<String, Object> row : rows) {
            BigDecimal target = dec(row.get("costTarget"));
            BigDecimal dynamic = dec(row.get("costDynamic"));
            if (target.signum() > 0 && dynamic.compareTo(target) > 0) {
                BigDecimal rate = dynamic.subtract(target).divide(target, 4, RoundingMode.HALF_UP);
                if (rate.compareTo(YELLOW_RATE) > 0) {
                    alerts.add(alert("COST", "HIGH", row.get("projectId"), String.valueOf(row.get("projectName")),
                            "成本超支（动态>目标，超支" + rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%）", "/cost"));
                }
            }
        }
        // 3) 招采围串标
        for (Map<String, Object> r : mapper.selectCollusionWarnings()) {
            alerts.add(alert("PROC", String.valueOf(r.get("riskLevel")), r.get("projectId"),
                    String.valueOf(r.get("title")), String.valueOf(r.get("detail")), "/proc"));
        }
        // 4) 费用超预算
        for (Map<String, Object> r : mapper.selectOverBudgetExpense()) {
            alerts.add(alert("EXPENSE", "MEDIUM", null,
                    orDefault(r.get("deptName"), "") + " · " + orDefault(r.get("accountName"), ""),
                    "预算余额为负", "/expense"));
        }
        return alerts;
    }

    private Map<String, Object> alert(String source, String level, Object projectId,
                                      String title, String desc, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", source);
        m.put("level", level);
        m.put("projectId", projectId);
        m.put("title", title);
        m.put("desc", desc);
        m.put("link", link);
        return m;
    }

    // ==================== 区域驾驶舱 ====================

    public Map<String, Object> region(Long orgId) {
        if (orgId == null) throw new BizException("请选择区域组织");
        List<Map<String, Object>> projects = mapper.selectProjectMetrics(orgId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orgId", orgId);
        r.put("projectCount", projects.size());
        r.put("projectMatrix", regionMatrix(projects));
        r.put("threeCalc", threeCalc(projects, orgId));
        r.put("fee", regionFee(orgId, projects));
        r.put("procurement", regionProcurement(orgId));
        return r;
    }

    private List<Map<String, Object>> regionMatrix(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal sold = dec(row.get("soldValue"));
            BigDecimal total = dec(row.get("totalRoomValue"));
            BigDecimal target = dec(row.get("costTarget"));
            BigDecimal dynamic = dec(row.get("costDynamic"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", row.get("projectId"));
            m.put("projectName", row.get("projectName"));
            m.put("city", row.get("city"));
            m.put("supplyValue", dec(row.get("supplyValue")));
            m.put("turnoverRate", ratio(sold, total));
            m.put("receiptAmount", dec(row.get("receiptAmount")));
            m.put("costDeviation", dynamic.subtract(target));
            m.put("costDeviationRate", target.signum() == 0 ? null
                    : dynamic.subtract(target).divide(target, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> threeCalc(List<Map<String, Object>> rows, Long orgId) {
        BigDecimal target = BigDecimal.ZERO;
        BigDecimal dynamic = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            target = target.add(dec(row.get("costTarget")));
            dynamic = dynamic.add(dec(row.get("costDynamic")));
        }
        BigDecimal actual = mapper.sumActualCost(orgId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetCost", target);
        m.put("dynamicCost", dynamic);
        m.put("actualCost", actual);
        m.put("dynamicVsTarget", dynamic.subtract(target));
        m.put("actualVsDynamic", actual.subtract(dynamic));
        return m;
    }

    private Map<String, Object> regionFee(Long orgId, List<Map<String, Object>> rows) {
        Map<String, Object> fee = mapper.regionFee(orgId);
        BigDecimal sales = dec(fee.get("salesFee"));
        BigDecimal fin = dec(fee.get("finFee"));
        BigDecimal total = dec(fee.get("total"));
        BigDecimal admin = total.subtract(sales).subtract(fin);
        BigDecimal signed = rows.stream().map(r -> dec(r.get("signedAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("salesFee", sales);
        m.put("adminFee", admin);
        m.put("finFee", fin);
        m.put("total", total);
        m.put("salesEfficiency", ratio(sales, signed)); // 营销费效=营销费/签约额
        m.put("adminEfficiency", ratio(admin, signed)); // 管理费效=管理费/签约额
        return m;
    }

    private Map<String, Object> regionProcurement(Long orgId) {
        Map<String, Object> p = mapper.regionProcurement(orgId);
        long total = lng(p.get("totalCount"));
        long publicBid = lng(p.get("publicBidCount"));
        BigDecimal budget = dec(p.get("budgetAmount"));
        BigDecimal award = dec(p.get("awardAmount"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalProjects", total);
        m.put("publicBidCount", publicBid);
        m.put("sunshineRatio", total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(publicBid).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        m.put("budgetAmount", budget);
        m.put("awardAmount", award);
        m.put("awardBudgetRatio", budget.signum() == 0 ? null
                : award.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP)); // 定标/立项比
        return m;
    }

    // ==================== 项目驾驶舱 ====================

    public Map<String, Object> project(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        Map<String, Object> info = mapper.selectProjectInfo(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project", info);
        r.put("timeline", mapper.selectProjectTimeline(projectId));
        r.put("valueWaterfall", valueWaterfall(projectId));
        r.put("dynamicProfit", dynamicProfitView(projectId));
        r.put("threeCalc", projectThreeCalc(projectId));
        r.put("alertDistribution", mapper.selectAlertDistribution(projectId));
        r.put("ledger", projectLedger(projectId));
        return r;
    }

    private Map<String, Object> valueWaterfall(Long projectId) {
        List<Map<String, Object>> flow = mapper.selectValueFlow(projectId);
        BigDecimal supply = BigDecimal.ZERO, sold = BigDecimal.ZERO, refund = BigDecimal.ZERO;
        for (Map<String, Object> f : flow) {
            supply = supply.add(dec(f.get("supplyValue")));
            sold = sold.add(dec(f.get("soldValue")));
            refund = refund.add(dec(f.get("refundValue")));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", flow);
        m.put("byCategory", mapper.selectValueLedger(projectId));
        m.put("totalSupply", supply);
        m.put("totalSold", sold);
        m.put("totalRefund", refund);
        return m;
    }

    private Map<String, Object> dynamicProfitView(Long projectId) {
        Map<String, Object> dp = mapper.selectDynamicProfitLatest(projectId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (dp != null) {
            m.put("snapMonth", dp.get("snapMonth"));
            m.put("dynamicValue", dec(dp.get("dynamicValue")));
            m.put("dynamicCost", dec(dp.get("dynamicCost")));
            m.put("threeFee", dec(dp.get("threeFee")));
            m.put("taxAmount", dec(dp.get("taxAmount")));
            m.put("dynamicProfit", dec(dp.get("dynamicProfit")));
            m.put("profitRate", dp.get("profitRate"));
            m.put("baselineProfit", dec(dp.get("baselineProfit")));
        } else {
            m.put("dynamicProfit", BigDecimal.ZERO);
        }
        return m;
    }

    private Map<String, Object> projectThreeCalc(Long projectId) {
        Map<String, Object> cost = mapper.selectProjectCost(projectId);
        BigDecimal target = cost == null ? BigDecimal.ZERO : dec(cost.get("targetCost"));
        BigDecimal dynamic = cost == null ? BigDecimal.ZERO : dec(cost.get("dynamicCost"));
        BigDecimal actual = mapper.sumProjectPaid(projectId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetCost", target);
        m.put("dynamicCost", dynamic);
        m.put("actualCost", actual);
        m.put("dynamicVsTarget", dynamic.subtract(target));
        m.put("actualVsDynamic", actual.subtract(dynamic));
        return m;
    }

    private Map<String, Object> projectLedger(Long projectId) {
        Map<String, Object> contract = mapper.selectContractLedger(projectId);
        Map<String, Object> change = mapper.selectChangeLedger(projectId);
        Map<String, Object> payment = mapper.selectPaymentLedger(projectId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contract", contract);
        m.put("change", change);
        m.put("payment", payment);
        return m;
    }

    // ==================== 专题分析 ====================

    public Map<String, Object> topic(String topic) {
        if (topic == null) throw new BizException("请选择专题");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("topic", topic);
        switch (topic) {
            case "value" -> r.put("data", valueTopic());
            case "cost" -> r.put("data", costTopic());
            case "sales" -> r.put("data", salesTopic());
            case "proc" -> r.put("data", procTopic());
            case "expense" -> r.put("data", expenseTopic());
            default -> throw new BizException("未知专题：" + topic + "（可选 value/cost/sales/proc/expense）");
        }
        return r;
    }

    private Map<String, Object> valueTopic() {
        List<Map<String, Object>> projects = mapper.selectProjectMetrics(null);
        BigDecimal sold = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (Map<String, Object> row : projects) {
            sold = sold.add(dec(row.get("soldValue")));
            total = total.add(dec(row.get("totalRoomValue")));
        }
        BigDecimal unsold = total.subtract(sold);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("distribution", mapper.selectValueDistribution());      // 货值分布(业态)
        m.put("saleTrend", mapper.selectSaleTrend());                // 去化速度(月度签约)
        m.put("stockSalesRatio", sold.signum() == 0 ? null
                : unsold.multiply(BigDecimal.valueOf(100)).divide(sold, 2, RoundingMode.HALF_UP)); // 存销比
        return m;
    }

    private Map<String, Object> costTopic() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountUnit", mapper.selectGroupCostByAccount());   // 科目单方对比
        m.put("changeRateRanking", mapper.selectChangeRateRanking()); // 变更率排行
        m.put("invalidCost", mapper.selectGroupInvalidCost());     // 无效成本
        return m;
    }

    private Map<String, Object> salesTopic() {
        BigDecimal avgContract = mapper.selectAvgContractPrice();
        BigDecimal avgTable = mapper.selectAvgTablePrice();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priceAchievement", new LinkedHashMap<String, Object>() {{
            put("avgContractPrice", avgContract);
            put("avgTablePrice", avgTable);
            put("rate", avgTable.signum() == 0 ? null
                    : avgContract.multiply(BigDecimal.valueOf(100)).divide(avgTable, 2, RoundingMode.HALF_UP));
        }});
        m.put("channelEffect", mapper.selectChannelEffect());       // 渠道效力
        return m;
    }

    private Map<String, Object> procTopic() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviation", mapper.selectProcDeviation());          // 定标价偏差/周期
        m.put("collusionStats", mapper.selectCollusionStats());    // 围串标统计
        return m;
    }

    private Map<String, Object> expenseTopic() {
        Map<String, Object> fee = mapper.regionFee(null);
        BigDecimal sales = dec(fee.get("salesFee"));
        BigDecimal fin = dec(fee.get("finFee"));
        BigDecimal total = dec(fee.get("total"));
        BigDecimal admin = total.subtract(sales).subtract(fin);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetExec", mapper.selectBudgetExecByDept());      // 预算执行
        m.put("threeFee", new LinkedHashMap<String, Object>() {{
            put("salesFee", sales);
            put("adminFee", admin);
            put("finFee", fin);
            put("total", total);
            put("salesRatio", ratio(sales, total));
            put("adminRatio", ratio(admin, total));
            put("finRatio", ratio(fin, total));
        }});
        return m;
    }

    // ==================== 下钻（汇总→明细） ====================

    public Map<String, Object> drill(String metricCode, Long orgId, Long projectId, String month) {
        String key = resolveDrillKey(metricCode);
        if (key == null) throw new BizException("未知指标：" + metricCode);
        List<Map<String, Object>> rows;
        String metric;
        switch (key) {
            case "contract" -> { rows = mapper.drillContracts(projectId); metric = "签约额"; }
            case "receipt" -> { rows = mapper.drillReceipts(projectId); metric = "回款额"; }
            case "cost" -> { rows = mapper.drillCostContracts(projectId); metric = "动态成本"; }
            case "room" -> { rows = mapper.drillRooms(projectId); metric = "去化率"; }
            case "profit" -> { rows = mapper.drillDynamicProfit(projectId); metric = "动态利润"; }
            case "change" -> { rows = mapper.drillChanges(projectId); metric = "变更率"; }
            case "plan" -> { rows = mapper.drillPlanTasks(projectId); metric = "计划达成率"; }
            case "expense" -> { rows = mapper.drillExpenseItems(); metric = "费效比"; }
            case "budget" -> { rows = mapper.drillBudget(); metric = "预算执行率"; }
            default -> throw new BizException("未知指标：" + metricCode);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("metricCode", metricCode);
        r.put("metric", metric);
        r.put("orgId", orgId);
        r.put("projectId", projectId);
        r.put("month", month);
        r.put("count", rows.size());
        r.put("rows", rows);
        return r;
    }

    /** 指标编码/名称 → 下钻明细类型 */
    private String resolveDrillKey(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SIGN_AMOUNT", "签约额" -> "contract";
            case "RECEIPT_AMOUNT", "回款额" -> "receipt";
            case "DYNAMIC_COST", "动态成本" -> "cost";
            case "TURNOVER_RATE", "去化率" -> "room";
            case "DYNAMIC_PROFIT", "动态利润" -> "profit";
            case "CHANGE_RATE", "变更率" -> "change";
            case "PLAN_ACHIEVE_RATE", "计划达成率" -> "plan";
            case "COST_EFFICIENCY", "费效比" -> "expense";
            case "BUDGET_EXEC_RATE", "预算执行率" -> "budget";
            default -> null;
        };
    }

    // ==================== 工具 ====================

    private String costLightOf(BigDecimal dynamic, BigDecimal target) {
        if (target.signum() <= 0) return null;
        if (dynamic.compareTo(target) <= 0) return "GREEN"; // 未超支（含低于预算）
        BigDecimal rate = dynamic.subtract(target).divide(target, 4, RoundingMode.HALF_UP);
        if (rate.compareTo(GREEN_RATE) <= 0) return "GREEN";
        if (rate.compareTo(YELLOW_RATE) <= 0) return "YELLOW";
        return "RED";
    }

    private String salesLightOf(BigDecimal sold, BigDecimal total) {
        if (total.signum() <= 0) return "RED";
        BigDecimal rate = sold.divide(total, 4, RoundingMode.HALF_UP);
        if (rate.compareTo(SELL_GREEN) >= 0) return "GREEN";
        if (rate.compareTo(SELL_YELLOW) >= 0) return "YELLOW";
        return "RED";
    }

    private String worst(String... lights) {
        boolean has = false;
        String worst = "GREEN";
        for (String l : lights) {
            if (l == null) continue;
            has = true;
            if ("RED".equals(l)) return "RED";
            if ("YELLOW".equals(l)) worst = "YELLOW";
        }
        return has ? worst : "NA";
    }

    private long planTotal(Map<String, Object> row) {
        return lng(row.get("planTotal"));
    }

    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) return null;
        if (part == null) part = BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static long lng(Object v) {
        return v == null ? 0L : new BigDecimal(v.toString()).longValue();
    }

    private static String orDefault(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }
}
