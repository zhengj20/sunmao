package com.reip.invest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.invest.entity.InvCalcVersion;
import com.reip.invest.entity.InvDynamicProfit;
import com.reip.invest.mapper.InvestMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态利润：月度刷新（事件驱动）+ 基线对比
 * 动态货值=Σ inv_value.total_value；动态成本=最新 cost_dynamic_snapshot.dynamic_total；
 * 三费=动态货值×4.5%（演示）；税金=动态货值×8%
 */
@Service
@RequiredArgsConstructor
public class InvestProfitService {

    /** 三费固定比例（演示值） */
    private static final BigDecimal THREE_FEE_RATE = new BigDecimal("0.045");
    /** 税金比例（演示值） */
    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    private final InvestMappers.ValueMapper valueMapper;
    private final InvestMappers.CostSnapshotMapper costSnapshotMapper;
    private final InvestMappers.DynamicProfitMapper dynamicProfitMapper;
    private final InvestMappers.CalcVersionMapper calcMapper;

    /**
     * 刷新项目动态利润（同月幂等：先查后更新/插入）
     */
    @Transactional
    public InvDynamicProfit refresh(Long projectId, String month) {
        if (projectId == null) throw new BizException("请选择项目");
        if (month == null || month.isBlank()) month = YearMonth.now().toString();

        BigDecimal dynamicValue = valueMapper.sumTotalValue(projectId);
        BigDecimal dynamicCost = costSnapshotMapper.selectLatestDynamicTotal(projectId);
        if (dynamicCost == null) dynamicCost = BigDecimal.ZERO;
        BigDecimal threeFee = dynamicValue.multiply(THREE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = dynamicValue.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = dynamicValue.subtract(dynamicCost).subtract(threeFee).subtract(tax)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = dynamicValue.compareTo(BigDecimal.ZERO) != 0
                ? profit.divide(dynamicValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;
        BigDecimal baseline = calcMapper.selectBaselineProfit(projectId);

        InvDynamicProfit snap = dynamicProfitMapper.selectOne(new LambdaQueryWrapper<InvDynamicProfit>()
                .eq(InvDynamicProfit::getProjectId, projectId)
                .eq(InvDynamicProfit::getSnapMonth, month));
        if (snap == null) {
            snap = new InvDynamicProfit();
            snap.setProjectId(projectId);
            snap.setSnapMonth(month);
            snap.setCreatedAt(LocalDateTime.now());
        }
        snap.setDynamicValue(dynamicValue);
        snap.setDynamicCost(dynamicCost);
        snap.setThreeFee(threeFee);
        snap.setTaxAmount(tax);
        snap.setDynamicProfit(profit);
        snap.setProfitRate(rate);
        snap.setBaselineProfit(baseline);
        if (snap.getId() == null) {
            dynamicProfitMapper.insert(snap);
        } else {
            dynamicProfitMapper.updateById(snap);
        }
        return snap;
    }

    /** 最新动态利润 + 对比拿地基线差异 */
    public Map<String, Object> latest(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        InvDynamicProfit latest = dynamicProfitMapper.selectOne(new LambdaQueryWrapper<InvDynamicProfit>()
                .eq(InvDynamicProfit::getProjectId, projectId)
                .orderByDesc(InvDynamicProfit::getSnapMonth)
                .last("LIMIT 1"));
        InvCalcVersion base = calcMapper.selectOne(new LambdaQueryWrapper<InvCalcVersion>()
                .eq(InvCalcVersion::getProjectId, projectId)
                .eq(InvCalcVersion::getLocked, 1)
                .orderByDesc(InvCalcVersion::getId)
                .last("LIMIT 1"));

        Map<String, Object> baseline = null;
        if (base != null) {
            baseline = new LinkedHashMap<>();
            baseline.put("versionCode", base.getVersionCode());
            baseline.put("netProfit", base.getNetProfit());
            baseline.put("profitRate", base.getProfitRate());
        }

        Map<String, Object> diff = null;
        if (latest != null && base != null && latest.getDynamicProfit() != null && base.getNetProfit() != null) {
            diff = new LinkedHashMap<>();
            diff.put("profitDiff", latest.getDynamicProfit().subtract(base.getNetProfit()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("latest", latest);
        result.put("baseline", baseline);
        result.put("diff", diff);
        return result;
    }
}
