package com.reip.invest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.invest.entity.InvCalcVersion;
import com.reip.invest.entity.InvDynamicProfit;
import com.reip.invest.entity.InvValueFlow;
import com.reip.invest.mapper.InvestMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资测算：版本管理 / 锁定基线 / 后评估
 */
@Service
@RequiredArgsConstructor
public class InvestCalcService {

    private static final DateTimeFormatter CODE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final InvestMappers.CalcVersionMapper calcMapper;
    private final InvestMappers.DynamicProfitMapper dynamicProfitMapper;
    private final InvestMappers.ValueMapper valueMapper;
    private final InvestMappers.ValueFlowMapper flowMapper;

    /** 版本分页 */
    public PageResult<InvCalcVersion> page(long current, long size, Long projectId, String stage, String status) {
        LambdaQueryWrapper<InvCalcVersion> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(InvCalcVersion::getProjectId, projectId);
        if (stage != null && !stage.isBlank()) qw.eq(InvCalcVersion::getStage, stage);
        if (status != null && !status.isBlank()) qw.eq(InvCalcVersion::getStatus, status);
        qw.orderByDesc(InvCalcVersion::getId);
        Page<InvCalcVersion> p = calcMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public InvCalcVersion detail(Long id) {
        InvCalcVersion v = calcMapper.selectById(id);
        if (v == null) throw new BizException("测算版本不存在");
        return v;
    }

    /** 新建版本：version_code 自动生成 CS-前缀+时间戳；净利率保存时算好 */
    @Transactional
    public Long create(InvCalcVersion v) {
        if (v.getProjectId() == null) throw new BizException("请选择项目");
        v.setId(null);
        v.setVersionCode("CS-" + LocalDateTime.now().format(CODE_TS));
        v.setLocked(0);
        v.setStatus("DRAFT");
        calcProfitRate(v);
        calcMapper.insert(v);
        return v.getId();
    }

    /** 修改（锁定后禁改） */
    @Transactional
    public void update(InvCalcVersion v) {
        InvCalcVersion old = detail(v.getId());
        if (old.getLocked() != null && old.getLocked() == 1) {
            throw new BizException("版本已锁定为后评估基准，禁止修改");
        }
        v.setVersionCode(null); // 编码不允许修改
        calcProfitRate(v);
        int updated = calcMapper.updateById(v);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        recalc(v.getId()); // 保存后重算 IRR + 动态回收期并落库
    }

    @Transactional
    public void delete(Long id) {
        InvCalcVersion old = detail(id);
        if (old.getLocked() != null && old.getLocked() == 1) {
            throw new BizException("版本已锁定为后评估基准，禁止删除");
        }
        calcMapper.deleteById(id);
    }

    /** 锁定：作为后评估基准 */
    @Transactional
    public void lock(Long id) {
        InvCalcVersion old = detail(id);
        if (old.getLocked() != null && old.getLocked() == 1) {
            throw new BizException("版本已是锁定状态");
        }
        InvCalcVersion up = new InvCalcVersion();
        up.setId(id);
        up.setVersion(old.getVersion());
        up.setLocked(1);
        up.setStatus("LOCKED");
        int updated = calcMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /**
     * 后评估：对比拿地测算基线与当前动态（最新动态利润快照），
     * 输出量/价/成本三因素差异分解（简化：货值差异、成本差异、净利差异）
     */
    public Map<String, Object> postEval(Long id) {
        InvCalcVersion base = detail(id);
        InvDynamicProfit latest = dynamicProfitMapper.selectOne(new LambdaQueryWrapper<InvDynamicProfit>()
                .eq(InvDynamicProfit::getProjectId, base.getProjectId())
                .orderByDesc(InvDynamicProfit::getSnapMonth)
                .last("LIMIT 1"));

        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("versionCode", base.getVersionCode());
        baseline.put("stage", base.getStage());
        baseline.put("saleValue", base.getSaleValue());
        baseline.put("totalCost", base.getTotalCost());
        baseline.put("netProfit", base.getNetProfit());
        baseline.put("profitRate", base.getProfitRate());

        Map<String, Object> current = new LinkedHashMap<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        if (latest != null) {
            current.put("snapMonth", latest.getSnapMonth());
            current.put("dynamicValue", latest.getDynamicValue());
            current.put("dynamicCost", latest.getDynamicCost());
            current.put("dynamicProfit", latest.getDynamicProfit());
            current.put("profitRate", latest.getProfitRate());

            diff.put("valueDiff", subtract(latest.getDynamicValue(), base.getSaleValue()));    // 量价差异（货值）
            diff.put("costDiff", subtract(latest.getDynamicCost(), base.getTotalCost()));      // 成本差异
            diff.put("profitDiff", subtract(latest.getDynamicProfit(), base.getNetProfit())); // 净利差异
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", base.getId());
        result.put("projectId", base.getProjectId());
        result.put("baseline", baseline);
        result.put("current", current);
        result.put("diff", diff);
        return result;
    }

    /**
     * 重新计算 IRR + 动态回收期并落库。
     * 现金流模型：CF[0]=-outflow（初始投资），CF[1..n]=net[m]（月度已售-退房）。
     * 无月度现金流数据时置 null 并给出提示，不造假数。
     */
    @Transactional
    public Map<String, Object> recalc(Long id) {
        InvCalcVersion v = detail(id);
        Long projectId = v.getProjectId();

        List<InvValueFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<InvValueFlow>()
                .eq(InvValueFlow::getProjectId, projectId)
                .orderByAsc(InvValueFlow::getFlowMonth));

        // 初始投资：totalCost；为 0 时用项目货值合计兜底
        BigDecimal outflow = v.getTotalCost();
        if (outflow == null || outflow.signum() == 0) {
            outflow = valueMapper.sumTotalValue(projectId);
        }

        List<BigDecimal> nets = new ArrayList<>();
        for (InvValueFlow f : flows) {
            BigDecimal sold = f.getSoldValue() == null ? BigDecimal.ZERO : f.getSoldValue();
            BigDecimal refund = f.getRefundValue() == null ? BigDecimal.ZERO : f.getRefundValue();
            nets.add(sold.subtract(refund));
        }

        boolean hasFlow = !nets.isEmpty();
        boolean validInvestment = outflow != null && outflow.signum() > 0;

        BigDecimal irr = null;
        BigDecimal payback = null;
        String hint = null;
        if (!hasFlow) {
            hint = "无月度现金流数据（inv_value_flow 为空），IRR 与动态回收期无法计算";
        } else if (!validInvestment) {
            hint = "初始投资为 0，IRR 与动态回收期无意义";
        } else {
            irr = computeIrr(outflow, nets);
            payback = computePayback(outflow, nets);
            if (irr == null) hint = "IRR 无解（现金流符号未翻转），已置空";
        }

        InvCalcVersion up = new InvCalcVersion();
        up.setId(id);
        up.setVersion(v.getVersion());
        up.setIrr(irr);
        up.setPaybackYears(payback);
        calcMapper.updateById(up);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("projectId", projectId);
        result.put("outflow", outflow);
        result.put("months", nets.size());
        result.put("irr", irr);
        result.put("paybackYears", payback);
        result.put("hint", hint);
        return result;
    }

    /** 二分法求 IRR：NPV(r)=Σ CF[t]/(1+r)^t=0，r∈[-0.9,10]，区间精度1e-6，最多200次 */
    private BigDecimal computeIrr(BigDecimal outflow, List<BigDecimal> nets) {
        double lo = -0.9, hi = 10.0;
        double flo = npv(lo, outflow, nets);
        double fhi = npv(hi, outflow, nets);
        if (flo == 0) return toPct(lo);
        if (fhi == 0) return toPct(hi);
        if (flo * fhi > 0) return null; // 符号未翻转，无解
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            double fmid = npv(mid, outflow, nets);
            if (Math.abs(hi - lo) < 1e-6) break;
            if (fmid == 0) { lo = hi = mid; break; }
            if (fmid * flo < 0) hi = mid;
            else { lo = mid; flo = fmid; }
        }
        return toPct((lo + hi) / 2);
    }

    private double npv(double r, BigDecimal outflow, List<BigDecimal> nets) {
        double v = -outflow.doubleValue();
        for (int t = 1; t <= nets.size(); t++) {
            v += nets.get(t - 1).doubleValue() / Math.pow(1 + r, t);
        }
        return v;
    }

    /** 动态回收期：累计净现金流（不折现）首次≥0 的月份 ÷ 12 换算为年 */
    private BigDecimal computePayback(BigDecimal outflow, List<BigDecimal> nets) {
        double cum = -outflow.doubleValue();
        for (int i = 0; i < nets.size(); i++) {
            cum += nets.get(i).doubleValue();
            if (cum >= 0) {
                return BigDecimal.valueOf(i + 1).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private BigDecimal toPct(double r) {
        return BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 净利率 = 净利润 / 总货值 × 100（保存时算好） */
    private void calcProfitRate(InvCalcVersion v) {
        if (v.getNetProfit() != null && v.getSaleValue() != null
                && v.getSaleValue().compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal rate = v.getNetProfit()
                    .divide(v.getSaleValue(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            v.setProfitRate(rate);
        } else {
            v.setProfitRate(null);
        }
    }

    private BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.subtract(b);
    }
}
