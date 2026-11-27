package com.reip.sales.service;

import com.reip.common.exception.BizException;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 销售报表：日报(认购/签约/回款三口径) / 去化率(已售/总货值) / 价格实现率(成交均价/表价均价)
 */
@Service
@RequiredArgsConstructor
public class SaleReportService {

    private final SaleMappers.ReportMapper reportMapper;

    /** 销售日报：认购 / 签约 / 回款 三口径 + 回款率 */
    public Map<String, Object> daily(Long projectId) {
        requireProject(projectId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("subscription", section(reportMapper.selectSubscriptionStats(projectId), "认购单数", "认购定金"));
        r.put("contract", section(reportMapper.selectContractStats(projectId), "签约单数", "签约金额"));
        r.put("receipt", section(reportMapper.selectReceiptStats(projectId), "实收笔数", "实收金额"));
        Map<String, Object> plan = reportMapper.selectPlanStats(projectId);
        BigDecimal total = dec(plan.get("total"));
        BigDecimal received = dec(plan.get("received"));
        r.put("collectionRate", total.signum() == 0 ? BigDecimal.ZERO
                : received.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
        return r;
    }

    /** 去化率：已售货值/总货值（并附套数口径） */
    public Map<String, Object> turnover(Long projectId) {
        requireProject(projectId);
        Map<String, Object> m = reportMapper.selectRoomStats(projectId);
        BigDecimal totalValue = dec(m.get("totalValue"));
        BigDecimal soldValue = dec(m.get("soldValue"));
        long totalRooms = lng(m.get("totalRooms"));
        long soldRooms = lng(m.get("soldRooms"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("totalRooms", totalRooms);
        r.put("soldRooms", soldRooms);
        r.put("totalValue", totalValue);
        r.put("soldValue", soldValue);
        r.put("turnoverRateValue", totalValue.signum() == 0 ? BigDecimal.ZERO
                : soldValue.multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, RoundingMode.HALF_UP));
        r.put("turnoverRateCount", totalRooms == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(soldRooms).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalRooms), 2, RoundingMode.HALF_UP));
        return r;
    }

    /** 价格实现率：成交均价 / 表价均价 */
    public Map<String, Object> priceAchievement(Long projectId) {
        requireProject(projectId);
        BigDecimal avgContract = reportMapper.selectAvgContractPrice(projectId);
        Map<String, Object> m = reportMapper.selectRoomStats(projectId);
        BigDecimal totalValue = dec(m.get("totalValue"));
        long totalRooms = lng(m.get("totalRooms"));
        BigDecimal avgTable = totalRooms == 0 ? BigDecimal.ZERO
                : totalValue.divide(BigDecimal.valueOf(totalRooms), 2, RoundingMode.HALF_UP);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        r.put("avgContractPrice", avgContract);
        r.put("avgTablePrice", avgTable);
        r.put("priceAchievementRate", avgTable.signum() == 0 ? BigDecimal.ZERO
                : avgContract.multiply(BigDecimal.valueOf(100)).divide(avgTable, 2, RoundingMode.HALF_UP));
        return r;
    }

    // ==================== 私有 ====================

    private void requireProject(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
    }

    private Map<String, Object> section(Map<String, Object> src, String cntKey, String amtKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", lng(src.get("cnt")));
        m.put("amount", dec(src.get("amt")));
        return m;
    }

    private static long lng(Object v) {
        return v == null ? 0L : new BigDecimal(v.toString()).longValue();
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}
