package com.reip.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.portal.entity.CockpitMetricDef;
import com.reip.portal.mapper.ReportMetricMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 指标字典：CRUD + 列表（按维度/状态筛选）
 */
@Service
@RequiredArgsConstructor
public class ReportMetricService {

    private final ReportMetricMapper mapper;

    public PageResult<CockpitMetricDef> page(long current, long size, String dimension, String status, String kw) {
        LambdaQueryWrapper<CockpitMetricDef> qw = new LambdaQueryWrapper<>();
        if (dimension != null && !dimension.isBlank()) qw.like(CockpitMetricDef::getDimension, dimension);
        if (status != null && !status.isBlank()) qw.eq(CockpitMetricDef::getStatus, status);
        if (kw != null && !kw.isBlank()) {
            qw.and(w -> w.like(CockpitMetricDef::getMetricName, kw)
                    .or().like(CockpitMetricDef::getMetricCode, kw));
        }
        qw.orderByAsc(CockpitMetricDef::getId);
        Page<CockpitMetricDef> p = mapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<CockpitMetricDef> list(String dimension, String status) {
        LambdaQueryWrapper<CockpitMetricDef> qw = new LambdaQueryWrapper<>();
        if (dimension != null && !dimension.isBlank()) qw.like(CockpitMetricDef::getDimension, dimension);
        if (status != null && !status.isBlank()) qw.eq(CockpitMetricDef::getStatus, status);
        qw.orderByAsc(CockpitMetricDef::getId);
        return mapper.selectList(qw);
    }

    public CockpitMetricDef detail(Long id) {
        CockpitMetricDef m = require(id);
        m.setVersionNo(mapper.selectCount(new LambdaQueryWrapper<CockpitMetricDef>()
                .eq(CockpitMetricDef::getMetricCode, m.getMetricCode())).intValue());
        return m;
    }

    @Transactional
    public Long create(CockpitMetricDef m) {
        if (m == null) throw new BizException("缺少指标内容");
        if (isBlank(m.getMetricCode())) throw new BizException("请填写指标编码");
        if (isBlank(m.getMetricName())) throw new BizException("请填写指标名称");
        if (mapper.selectCount(new LambdaQueryWrapper<CockpitMetricDef>()
                .eq(CockpitMetricDef::getMetricCode, m.getMetricCode())) > 0) {
            throw new BizException("指标编码已存在：" + m.getMetricCode());
        }
        m.setId(null);
        if (isBlank(m.getStatus())) m.setStatus("ACTIVE");
        mapper.insert(m);
        return m.getId();
    }

    @Transactional
    public void update(Long id, CockpitMetricDef m) {
        CockpitMetricDef cur = require(id);
        if (m.getMetricCode() != null && !m.getMetricCode().equals(cur.getMetricCode())) {
            if (mapper.selectCount(new LambdaQueryWrapper<CockpitMetricDef>()
                    .eq(CockpitMetricDef::getMetricCode, m.getMetricCode())) > 0) {
                throw new BizException("指标编码已存在：" + m.getMetricCode());
            }
            cur.setMetricCode(m.getMetricCode());
        }
        if (!isBlank(m.getMetricName())) cur.setMetricName(m.getMetricName());
        if (m.getFormula() != null) cur.setFormula(m.getFormula());
        if (m.getSourceTable() != null) cur.setSourceTable(m.getSourceTable());
        if (m.getDimension() != null) cur.setDimension(m.getDimension());
        if (m.getOwner() != null) cur.setOwner(m.getOwner());
        if (m.getStatus() != null) cur.setStatus(m.getStatus());
        if (m.getEffectiveDate() != null) cur.setEffectiveDate(m.getEffectiveDate());
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private CockpitMetricDef require(Long id) {
        if (id == null) throw new BizException("缺少指标ID");
        CockpitMetricDef m = mapper.selectById(id);
        if (m == null) throw new BizException("指标不存在");
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
