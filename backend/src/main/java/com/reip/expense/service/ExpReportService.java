package com.reip.expense.service;

import com.reip.expense.mapper.ExpenseMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 费用分析：预算执行(部门/科目/组织三视角) / 三费占比·费效比 / 异常识别(环比突增·高频报销人)
 */
@Service
@RequiredArgsConstructor
public class ExpReportService {

    private final ExpenseMappers.ReportMapper reportMapper;

    /** 预算执行分析：部门 / 科目 / 组织(项目)三视角 + 合计 */
    public Map<String, Object> exec() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("byDept", reportMapper.execByDept());
        r.put("byAccount", reportMapper.execByAccount());
        r.put("byOrg", reportMapper.execByOrg());
        r.put("total", reportMapper.execTotal());
        return r;
    }

    /** 三费占比 + 费效比（营销费/签约额） */
    public Map<String, Object> threeFee() {
        Map<String, Object> fee = reportMapper.threeFee();
        BigDecimal total = dec(fee.get("total"));
        BigDecimal sales = dec(fee.get("salesFee"));
        BigDecimal fin = dec(fee.get("finFee"));
        BigDecimal admin = dec(fee.get("adminFee"));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("salesFee", sales);
        r.put("adminFee", admin);
        r.put("finFee", fin);
        r.put("salesRatio", ratio(sales, total));
        r.put("adminRatio", ratio(admin, total));
        r.put("finRatio", ratio(fin, total));

        BigDecimal signed = reportMapper.signedTotal();
        r.put("signedTotal", signed);
        // 费效比 = 营销费 / 签约额
        r.put("costEfficiencyRatio", signed.signum() == 0 ? BigDecimal.ZERO
                : sales.multiply(BigDecimal.valueOf(100)).divide(signed, 2, RoundingMode.HALF_UP));
        return r;
    }

    /** 费用总览：预算合计 + 待办计数 + 最近月支出 + 近6月趋势 + 部门 Top5 */
    public Map<String, Object> overview() {
        List<Map<String, Object>> trend = reportMapper.monthlyTrend();
        List<Map<String, Object>> byDept = reportMapper.execByDept();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("budget", reportMapper.execTotal());
        r.put("counts", reportMapper.counts());
        r.put("monthExpense", trend.isEmpty() ? null : trend.get(trend.size() - 1));
        r.put("trend", trend.size() > 6 ? trend.subList(trend.size() - 6, trend.size()) : trend);
        r.put("topDept", byDept.size() > 5 ? byDept.subList(0, 5) : byDept);
        return r;
    }

    /** 异常识别：环比突增 + 高频报销人 */
    public Map<String, Object> anomaly() {
        List<Map<String, Object>> trend = reportMapper.monthlyTrend();
        List<Map<String, Object>> top = reportMapper.topReimbursers();

        // 环比突增：最近月 vs 上一月，增幅 > 30% 视为突增
        List<Map<String, Object>> surges = new java.util.ArrayList<>();
        for (int i = 1; i < trend.size(); i++) {
            Map<String, Object> cur = trend.get(i);
            Map<String, Object> prev = trend.get(i - 1);
            BigDecimal curAmt = dec(cur.get("amt"));
            BigDecimal prevAmt = dec(prev.get("amt"));
            if (prevAmt.signum() > 0) {
                BigDecimal growth = curAmt.subtract(prevAmt).multiply(BigDecimal.valueOf(100))
                        .divide(prevAmt, 2, RoundingMode.HALF_UP);
                if (growth.compareTo(new BigDecimal("30")) > 0) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("month", cur.get("month"));
                    s.put("prevMonth", prev.get("month"));
                    s.put("amount", curAmt);
                    s.put("prevAmount", prevAmt);
                    s.put("growthPct", growth);
                    surges.add(s);
                }
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("monthlyTrend", trend);
        r.put("surges", surges);
        r.put("topReimbursers", top);
        return r;
    }

    // ==================== 工具 ====================

    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
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
}
