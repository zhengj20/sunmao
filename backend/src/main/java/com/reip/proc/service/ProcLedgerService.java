package com.reip.proc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.proc.mapper.ProcMappers.LedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招采台账：全流程台账（立项维度 JOIN 定标）与统计
 */
@Service
@RequiredArgsConstructor
public class ProcLedgerService {

    private final LedgerMapper ledgerMapper;

    /** 全流程台账：立项编号/标的/方式/预算/中标人/定标价/偏差/偏差率/周期天数/状态 */
    public PageResult<Map<String, Object>> page(long current, long size, Long projectId, String kw) {
        Page<?> page = new Page<>(current, size);
        List<Map<String, Object>> rows = ledgerMapper.selectLedgerPage(page, projectId, kw);
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 招采过程总览：9 节点完成标记（需求→…→合同），供地铁图看板渲染 */
    public PageResult<Map<String, Object>> overview(long current, long size, Long projectId,
                                                    String method, String status, String kw) {
        Page<?> page = new Page<>(current, size);
        List<Map<String, Object>> rows = ledgerMapper.selectOverviewPage(page, projectId, method, status, kw);
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 统计：采购周期平均天数、定标价与预算平均偏差率、公开招标占比、直接委托占比 */
    public Map<String, Object> stats(Long projectId) {
        if (projectId == null) throw new BizException("缺少项目ID");
        Map<String, Object> methodStats = ledgerMapper.selectMethodStats(projectId);
        Map<String, Object> awardStats = ledgerMapper.selectAwardStats(projectId);
        BigDecimal directAmount = ledgerMapper.sumDirectAmount(projectId);
        BigDecimal awardAmount = ledgerMapper.sumAwardAmount(projectId);

        long totalCount = toLong(methodStats.get("totalCount"));
        long publicBidCount = toLong(methodStats.get("publicBidCount"));
        BigDecimal publicBidPct = totalCount > 0
                ? BigDecimal.valueOf(publicBidCount * 100L)
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalAmount = directAmount.add(awardAmount);
        BigDecimal directPct = totalAmount.compareTo(BigDecimal.ZERO) > 0
                ? directAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", projectId);
        m.put("projectCount", totalCount);
        m.put("publicBidCount", publicBidCount);
        m.put("publicBidPct", publicBidPct);
        m.put("awardCount", toLong(awardStats.get("awardCount")));
        m.put("avgCycleDays", awardStats.get("avgCycleDays"));
        m.put("avgDeviationPct", awardStats.get("avgDeviationPct"));
        m.put("directAmount", directAmount);
        m.put("directPct", directPct);
        m.put("sunlightPct", BigDecimal.valueOf(100).subtract(directPct));
        return m;
    }

    private long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
