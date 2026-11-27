package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostIndicator;
import com.reip.cost.mapper.CostMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 成本指标库：集团/项目指标维护与测算引用
 */
@Service
@RequiredArgsConstructor
public class CostIndicatorService {

    private final CostMappers.IndicatorMapper indicatorMapper;

    public PageResult<CostIndicator> page(long current, long size, Long projectId, String accountCode,
                                          String productCategory, String indicatorType, String source, String kw) {
        LambdaQueryWrapper<CostIndicator> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostIndicator::getProjectId, projectId);
        if (accountCode != null && !accountCode.isBlank()) qw.eq(CostIndicator::getAccountCode, accountCode);
        if (productCategory != null && !productCategory.isBlank())
            qw.eq(CostIndicator::getProductCategory, productCategory);
        if (indicatorType != null && !indicatorType.isBlank())
            qw.eq(CostIndicator::getIndicatorType, indicatorType);
        if (source != null && !source.isBlank()) qw.like(CostIndicator::getSource, source);
        if (kw != null && !kw.isBlank()) qw.like(CostIndicator::getIndicatorName, kw);
        qw.orderByDesc(CostIndicator::getId);
        Page<CostIndicator> p = indicatorMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostIndicator detail(Long id) {
        CostIndicator indicator = indicatorMapper.selectById(id);
        if (indicator == null) throw new BizException("指标不存在");
        return indicator;
    }

    @Transactional
    public Long create(CostIndicator indicator) {
        if (indicator == null) throw new BizException("缺少表单内容");
        if (indicator.getIndicatorName() == null || indicator.getIndicatorName().isBlank())
            throw new BizException("请填写指标名称");
        if (indicator.getValue() == null) throw new BizException("请填写指标值");
        indicator.setId(null);
        indicatorMapper.insert(indicator);
        return indicator.getId();
    }

    @Transactional
    public void update(CostIndicator indicator) {
        if (indicator == null || indicator.getId() == null) throw new BizException("缺少指标ID");
        if (indicatorMapper.selectById(indicator.getId()) == null) throw new BizException("指标不存在");
        if (indicatorMapper.updateById(indicator) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        if (indicatorMapper.selectById(id) == null) throw new BizException("指标不存在");
        indicatorMapper.deleteById(id);
    }

    /** 匹配指标供测算引用（科目+业态过滤） */
    public List<CostIndicator> suggest(String accountCode, String productCategory) {
        LambdaQueryWrapper<CostIndicator> qw = new LambdaQueryWrapper<>();
        if (accountCode != null && !accountCode.isBlank()) qw.eq(CostIndicator::getAccountCode, accountCode);
        if (productCategory != null && !productCategory.isBlank())
            qw.eq(CostIndicator::getProductCategory, productCategory);
        qw.orderByDesc(CostIndicator::getId);
        return indicatorMapper.selectList(qw);
    }
}
