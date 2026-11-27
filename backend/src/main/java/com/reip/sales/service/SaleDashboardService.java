package com.reip.sales.service;

import com.reip.common.exception.BizException;
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
 * 销售驾驶舱 / 销售总览 / 置业顾问业绩 / 蓄客漏斗 / 渠道结构 —— 只读聚合
 */
@Service
@RequiredArgsConstructor
public class SaleDashboardService {

    private final SaleMappers.ReportMapper reportMapper;
    private final SaleMappers.DashboardMapper dashMapper;
    private final SaleSupport support;

    // ==================== 销售驾驶舱 ====================

    public Map<String, Object> cockpit(Long projectId) {
        requireProject(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("kpi", kpi(projectId));
        r.put("contractTrend", dashMapper.selectMonthlyContract(projectId));
        r.put("receiptTrend", dashMapper.selectMonthlyReceipt(projectId));
        r.put("channel", channelStructure(projectId));
        r.put("layout", dashMapper.selectLayoutBoard(projectId));
        r.put("topAdvisors", advisorList(projectId));
        r.put("funnel", funnelStages(projectId));
        return r;
    }

    // ==================== 销售总览 ====================

    public Map<String, Object> overview(Long projectId) {
        requireProject(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);

        Map<String, Object> room = reportMapper.selectRoomStats(projectId);
        Map<String, Object> plan = reportMapper.selectPlanStats(projectId);
        BigDecimal totalValue = dec(room.get("totalValue"));
        BigDecimal soldValue = dec(room.get("soldValue"));
        BigDecimal totalPlan = dec(plan.get("total"));
        BigDecimal received = dec(plan.get("received"));

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("totalRooms", lng(room.get("totalRooms")));
        progress.put("soldRooms", lng(room.get("soldRooms")));
        progress.put("totalValue", totalValue);
        progress.put("soldValue", soldValue);
        progress.put("unsoldValue", totalValue.subtract(soldValue));
        progress.put("turnoverRate", pct(soldValue, totalValue));
        progress.put("saleAmount", dec(reportMapper.selectContractStats(projectId).get("amt")));
        progress.put("receiptAmount", dec(reportMapper.selectReceiptStats(projectId).get("amt")));
        progress.put("collectionRate", pct(received, totalPlan));
        progress.put("avgContractPrice", reportMapper.selectAvgContractPrice(projectId));
        r.put("progress", progress);

        List<Map<String, Object>> buildings = dashMapper.selectBuildingBoard(projectId);
        for (Map<String, Object> b : buildings) {
            b.put("soldRate", pct(dec(b.get("soldValue")), dec(b.get("totalValue"))));
            b.put("countRate", pct2(lng(b.get("sold")), lng(b.get("total"))));
        }
        r.put("buildingBoard", buildings);

        List<Map<String, Object>> layouts = dashMapper.selectLayoutBoard(projectId);
        for (Map<String, Object> l : layouts) {
            l.put("soldRate", pct2(lng(l.get("sold")), lng(l.get("total"))));
        }
        r.put("layoutBoard", layouts);

        r.put("priceTrend", dashMapper.selectMonthlyAvgPrice(projectId));
        return r;
    }

    // ==================== 置业顾问业绩 ====================

    public Map<String, Object> advisor(Long projectId) {
        requireProject(projectId);
        List<Map<String, Object>> list = advisorList(projectId);
        BigDecimal subAmt = BigDecimal.ZERO, conAmt = BigDecimal.ZERO, recAmt = BigDecimal.ZERO;
        int rank = 1;
        for (Map<String, Object> a : list) {
            a.put("rank", rank++);
            subAmt = subAmt.add(dec(a.get("subAmount")));
            conAmt = conAmt.add(dec(a.get("conAmount")));
            recAmt = recAmt.add(dec(a.get("recAmount")));
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("advisorCount", list.size());
        total.put("totalSubAmount", subAmt);
        total.put("totalConAmount", conAmt);
        total.put("totalRecAmount", recAmt);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("total", total);
        r.put("list", list);
        return r;
    }

    // ==================== 蓄客漏斗 ====================

    public Map<String, Object> funnel(Long projectId) {
        requireProject(projectId);
        List<Map<String, Object>> stages = funnelStages(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("stages", stages);
        r.put("conversions", funnelConversions(projectId, stages));
        return r;
    }

    // ==================== 渠道结构 ====================

    public Map<String, Object> channelStructure(Long projectId) {
        requireProject(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("visitByChannel", dashMapper.selectVisitByChannel(projectId));
        r.put("contractBySupplier", dashMapper.selectContractBySupplier(projectId));
        return r;
    }

    // ==================== 私有 ====================

    private Map<String, Object> kpi(Long projectId) {
        Map<String, Object> c = reportMapper.selectContractStats(projectId);
        Map<String, Object> rec = reportMapper.selectReceiptStats(projectId);
        Map<String, Object> room = reportMapper.selectRoomStats(projectId);
        Map<String, Object> plan = reportMapper.selectPlanStats(projectId);
        long visit = dashMapper.selectVisitCount(projectId);
        long conCnt = lng(c.get("cnt"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saleAmount", dec(c.get("amt")));
        m.put("saleCount", conCnt);
        m.put("receiptAmount", dec(rec.get("amt")));
        m.put("receiptCount", lng(rec.get("cnt")));
        m.put("collectionRate", pct(dec(plan.get("received")), dec(plan.get("total"))));
        m.put("turnoverRate", pct(dec(room.get("soldValue")), dec(room.get("totalValue"))));
        m.put("visitCount", visit);
        m.put("contractRate", pct2(conCnt, visit));
        return m;
    }

    private List<Map<String, Object>> funnelStages(Long projectId) {
        long visit = dashMapper.selectVisitCount(projectId);
        long sub = lng(reportMapper.selectSubscriptionStats(projectId).get("cnt"));
        long con = lng(reportMapper.selectContractStats(projectId).get("cnt"));
        long paid = dashMapper.selectPaidContractCount(projectId);
        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(stage("visit", "来访", visit));
        stages.add(stage("subscription", "认购", sub));
        stages.add(stage("contract", "签约", con));
        stages.add(stage("paid", "回款", paid));
        return stages;
    }

    private List<Map<String, Object>> funnelConversions(Long projectId, List<Map<String, Object>> stages) {
        Map<String, Long> byKey = new LinkedHashMap<>();
        for (Map<String, Object> s : stages) byKey.put((String) s.get("stage"), (Long) s.get("count"));
        List<Map<String, Object>> conv = new ArrayList<>();
        conv.add(conv("visit", "来访", "subscription", "认购", byKey));
        conv.add(conv("subscription", "认购", "contract", "签约", byKey));
        conv.add(conv("contract", "签约", "paid", "回款", byKey));
        return conv;
    }

    private List<Map<String, Object>> advisorList(Long projectId) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        mergeAdvisor(map, dashMapper.selectAdvisorSub(projectId), "subCount", "subAmount");
        mergeAdvisor(map, dashMapper.selectAdvisorCon(projectId), "conCount", "conAmount");
        mergeAdvisor(map, dashMapper.selectAdvisorRec(projectId), "recCount", "recAmount");
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        list.sort((a, b) -> dec(b.get("conAmount")).compareTo(dec(a.get("conAmount"))));
        for (Map<String, Object> e : list) {
            Long agentId = e.get("agentId") == null ? null : Long.valueOf(e.get("agentId").toString());
            e.put("agentName", support.agentName(agentId));
        }
        return list;
    }

    private void mergeAdvisor(Map<Long, Map<String, Object>> map, List<Map<String, Object>> rows, String cntKey, String amtKey) {
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("agentId");
            if (idObj == null) continue;
            Long id = Long.valueOf(idObj.toString());
            Map<String, Object> agg = map.computeIfAbsent(id, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("agentId", id);
                m.put("subCount", 0L);
                m.put("subAmount", BigDecimal.ZERO);
                m.put("conCount", 0L);
                m.put("conAmount", BigDecimal.ZERO);
                m.put("recCount", 0L);
                m.put("recAmount", BigDecimal.ZERO);
                return m;
            });
            agg.put(cntKey, lng(row.get(cntKey)));
            agg.put(amtKey, dec(row.get(amtKey)));
        }
    }

    private Map<String, Object> stage(String key, String name, long count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", key);
        m.put("name", name);
        m.put("count", count);
        return m;
    }

    private Map<String, Object> conv(String fromKey, String fromName, String toKey, String toName, Map<String, Long> byKey) {
        long from = byKey.getOrDefault(fromKey, 0L);
        long to = byKey.getOrDefault(toKey, 0L);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", fromName);
        m.put("to", toName);
        m.put("rate", pct2(to, from));
        return m;
    }

    private void requireProject(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
    }

    private static long lng(Object v) {
        return v == null ? 0L : new BigDecimal(v.toString()).longValue();
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }

    private static BigDecimal pct(BigDecimal num, BigDecimal den) {
        if (num == null || den == null || den.signum() == 0) return BigDecimal.ZERO;
        return num.multiply(BigDecimal.valueOf(100)).divide(den, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct2(long num, long den) {
        if (den == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(num).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(den), 2, RoundingMode.HALF_UP);
    }
}
