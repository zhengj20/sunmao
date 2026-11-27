package com.reip.invest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.invest.entity.InvValue;
import com.reip.invest.mapper.InvestMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 货值台账 + 供销存
 */
@Service
@RequiredArgsConstructor
public class InvestValueService {

    private final InvestMappers.ValueMapper valueMapper;
    private final InvestMappers.ValueFlowMapper flowMapper;

    public PageResult<InvValue> page(long current, long size, Long projectId, String productCategory) {
        LambdaQueryWrapper<InvValue> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(InvValue::getProjectId, projectId);
        if (productCategory != null && !productCategory.isBlank()) qw.eq(InvValue::getProductCategory, productCategory);
        qw.orderByDesc(InvValue::getId);
        Page<InvValue> p = valueMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public InvValue detail(Long id) {
        InvValue v = valueMapper.selectById(id);
        if (v == null) throw new BizException("货值台账记录不存在");
        return v;
    }

    /** 新增：货值=面积×单价 自动计算 */
    @Transactional
    public Long create(InvValue v) {
        if (v.getProjectId() == null) throw new BizException("请选择项目");
        v.setId(null);
        v.setTotalValue(calcTotalValue(v));
        valueMapper.insert(v);
        return v.getId();
    }

    @Transactional
    public void update(InvValue v) {
        detail(v.getId());
        v.setTotalValue(calcTotalValue(v));
        int updated = valueMapper.updateById(v);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        valueMapper.deleteById(id);
    }

    /**
     * 供销存：期初存货 + 新供货 − 已售 + 退房
     * 新供货/已售/退房从 inv_value_flow 汇总；期末存货= inv_value 合计，期初倒算
     */
    public Map<String, Object> stock(Long projectId) {
        BigDecimal supply = nvl(flowMapper.sumSupply(projectId));
        BigDecimal sold = nvl(flowMapper.sumSold(projectId));
        BigDecimal refund = nvl(flowMapper.sumRefund(projectId));
        BigDecimal endStock = nvl(valueMapper.sumTotalValue(projectId));
        // 期末 = 期初 + 供货 - 已售 + 退房 → 期初 = 期末 - 供货 + 已售 - 退房
        BigDecimal beginStock = endStock.subtract(supply).add(sold).subtract(refund);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("beginStock", beginStock);
        result.put("supply", supply);
        result.put("sold", sold);
        result.put("refund", refund);
        result.put("endStock", endStock);
        return result;
    }

    /** 货值 = 面积 × 单价 */
    private BigDecimal calcTotalValue(InvValue v) {
        if (v.getArea() == null || v.getPrice() == null) return null;
        return v.getArea().multiply(v.getPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
