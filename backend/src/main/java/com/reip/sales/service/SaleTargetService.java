package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.sales.entity.SaleTarget;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 销售目标：CRUD + 周期实际完成值与达成率
 */
@Service
@RequiredArgsConstructor
public class SaleTargetService {

    private final SaleMappers.TargetMapper targetMapper;
    private final SaleMappers.DashboardMapper dashMapper;
    private final SaleSupport support;

    public PageResult<SaleTarget> page(long current, long size, Long projectId, String periodType, String metric) {
        LambdaQueryWrapper<SaleTarget> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleTarget::getProjectId, projectId);
        if (periodType != null && !periodType.isBlank()) qw.eq(SaleTarget::getPeriodType, periodType);
        if (metric != null && !metric.isBlank()) qw.eq(SaleTarget::getMetric, metric);
        qw.orderByAsc(SaleTarget::getPeriodType).orderByDesc(SaleTarget::getPeriod).orderByDesc(SaleTarget::getId);
        Page<SaleTarget> p = targetMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleTarget detail(Long id) {
        SaleTarget t = require(id);
        fillDisplay(t);
        return t;
    }

    @Transactional
    public Long create(SaleTarget t) {
        if (t == null) throw new BizException("缺少目标");
        if (t.getProjectId() == null) throw new BizException("请选择项目");
        if (t.getPeriodType() == null || t.getPeriodType().isBlank()) throw new BizException("请选择周期类型");
        if (t.getPeriod() == null || t.getPeriod().isBlank()) throw new BizException("请填写周期(如 2026-09)");
        if (t.getTargetValue() == null) throw new BizException("请填写目标值");
        if (t.getMetric() == null || t.getMetric().isBlank()) t.setMetric("AMOUNT");
        if (t.getStatus() == null || t.getStatus().isBlank()) t.setStatus("ACTIVE");
        t.setId(null);
        targetMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public void update(SaleTarget t) {
        if (t == null || t.getId() == null) throw new BizException("缺少目标ID");
        require(t.getId());
        if (targetMapper.updateById(t) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        targetMapper.deleteById(id);
    }

    // ==================== 私有 ====================

    private void fillDisplay(SaleTarget t) {
        t.setAgentName(t.getAgentId() == null ? null : support.agentName(t.getAgentId()));
        try {
            LocalDate[] range = periodRange(t.getPeriodType(), t.getPeriod());
            BigDecimal actual = actualOf(t, range);
            t.setActualValue(actual);
            BigDecimal target = SaleSupport.nvl(t.getTargetValue());
            t.setAchievementRate(target.signum() == 0 ? BigDecimal.ZERO
                    : actual.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP));
        } catch (Exception ignore) {
            t.setActualValue(null);
            t.setAchievementRate(null);
        }
    }

    private BigDecimal actualOf(SaleTarget t, LocalDate[] range) {
        LocalDate from = range[0], to = range[1];
        switch (t.getMetric() == null ? "AMOUNT" : t.getMetric()) {
            case "COUNT": return BigDecimal.valueOf(dashMapper.selectActualCount(t.getProjectId(), from, to));
            case "AREA": return dashMapper.selectActualArea(t.getProjectId(), from, to);
            default: return dashMapper.selectActualAmount(t.getProjectId(), from, to);
        }
    }

    /** 周期 → [起, 止) 日期区间 */
    private LocalDate[] periodRange(String periodType, String period) {
        String p = period == null ? "" : period.trim();
        if ("YEAR".equalsIgnoreCase(periodType)) {
            int y = Integer.parseInt(p);
            return new LocalDate[]{LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1)};
        }
        if ("QUARTER".equalsIgnoreCase(periodType)) {
            int y = Integer.parseInt(p.substring(0, p.indexOf('-')));
            int q = Integer.parseInt(p.substring(p.lastIndexOf('Q') + 1));
            LocalDate from = LocalDate.of(y, (q - 1) * 3 + 1, 1);
            return new LocalDate[]{from, from.plusMonths(3)};
        }
        // MONTH：2026-09
        int y = Integer.parseInt(p.substring(0, 4));
        int m = Integer.parseInt(p.substring(5, 7));
        LocalDate from = LocalDate.of(y, m, 1);
        return new LocalDate[]{from, from.plusMonths(1)};
    }

    private SaleTarget require(Long id) {
        if (id == null) throw new BizException("缺少目标ID");
        SaleTarget t = targetMapper.selectById(id);
        if (t == null) throw new BizException("销售目标不存在");
        return t;
    }
}
