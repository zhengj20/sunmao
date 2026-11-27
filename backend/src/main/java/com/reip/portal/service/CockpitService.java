package com.reip.portal.service;

import com.reip.portal.mapper.CockpitMapper;
import com.reip.portal.mapper.CockpitV2Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱：集团经营卡片 + 项目红黄绿灯 + 近6个月趋势
 */
@Service
@RequiredArgsConstructor
public class CockpitService {

    /** 偏差阈值：≤2%绿灯 / ≤5%黄灯 / >5%红灯 */
    private static final BigDecimal GREEN_RATE = new BigDecimal("0.02");
    private static final BigDecimal YELLOW_RATE = new BigDecimal("0.05");

    private final CockpitMapper cockpitMapper;
    private final CockpitV2Mapper cockpitV2Mapper;

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cards", cards());
        result.put("projects", projects());
        result.put("trend", trend());
        return result;
    }

    /** 集团经营指标卡片（全部真实取数） */
    private Map<String, Object> cards() {
        BigDecimal saleValue = cockpitMapper.sumSaleValue();
        BigDecimal dynamicCost = cockpitMapper.sumLatestDynamicCost();
        BigDecimal dynamicProfit = cockpitMapper.sumLatestDynamicProfit();

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("signedAmount", cockpitV2Mapper.sumSignedAmount());   // Σ sale_contract（生效/网签）
        cards.put("receiptAmount", cockpitV2Mapper.sumReceiptAmount()); // Σ sale_receipt（实收）
        cards.put("supplyValue", saleValue);                            // 供货货值 Σ inv_value
        cards.put("totalValue", cockpitMapper.sumLockedSaleValue());    // 总货值（拿地锁定基线）
        cards.put("dynamicCost", dynamicCost);                          // 动态成本（最新快照）
        cards.put("dynamicProfit", dynamicProfit);                      // 动态利润（最新快照）
        return cards;
    }

    /** 项目级动态成本监控（红黄绿灯） */
    private List<Map<String, Object>> projects() {
        List<Map<String, Object>> projects = cockpitMapper.selectProjects();
        for (Map<String, Object> p : projects) {
            BigDecimal dynamicCost = toDecimal(p.get("dynamicCost"));
            BigDecimal targetTotal = toDecimal(p.get("targetTotal"));
            BigDecimal costDeviation = null;
            String light = null;
            if (dynamicCost != null && targetTotal != null) {
                costDeviation = dynamicCost.subtract(targetTotal);
                light = lightOf(dynamicCost, targetTotal);
            }
            p.put("costDeviation", costDeviation);
            p.put("light", light);
        }
        return projects;
    }

    /** 近6个月动态利润/动态成本序列 */
    private List<Map<String, Object>> trend() {
        String startMonth = YearMonth.now().minusMonths(5).toString();
        Map<String, BigDecimal> profitByMonth = new LinkedHashMap<>();
        for (Map<String, Object> r : cockpitMapper.selectProfitTrend(startMonth)) {
            profitByMonth.put(String.valueOf(r.get("month")), toDecimal(r.get("dynamicProfit")));
        }
        Map<String, BigDecimal> costByMonth = new LinkedHashMap<>();
        for (Map<String, Object> r : cockpitMapper.selectCostTrend(startMonth)) {
            costByMonth.put(String.valueOf(r.get("month")), toDecimal(r.get("dynamicCost")));
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        YearMonth m = YearMonth.now().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            String month = m.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month);
            row.put("dynamicProfit", profitByMonth.getOrDefault(month, BigDecimal.ZERO));
            row.put("dynamicCost", costByMonth.getOrDefault(month, BigDecimal.ZERO));
            trend.add(row);
            m = m.plusMonths(1);
        }
        return trend;
    }

    /** 灯色：偏差绝对比例 ≤2% GREEN / ≤5% YELLOW / >5% RED */
    private String lightOf(BigDecimal dynamicCost, BigDecimal targetTotal) {
        if (targetTotal.compareTo(BigDecimal.ZERO) <= 0) return "RED";
        BigDecimal rate = dynamicCost.subtract(targetTotal).abs()
                .divide(targetTotal, 4, RoundingMode.HALF_UP);
        if (rate.compareTo(GREEN_RATE) <= 0) return "GREEN";
        if (rate.compareTo(YELLOW_RATE) <= 0) return "YELLOW";
        return "RED";
    }

    private BigDecimal toDecimal(Object v) {
        return v == null ? null : (v instanceof BigDecimal b ? b : new BigDecimal(v.toString()));
    }
}
