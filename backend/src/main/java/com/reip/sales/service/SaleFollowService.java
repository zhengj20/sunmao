package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.sales.entity.SaleFollow;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户跟进：客户池概览 / 跟进记录CRUD / 逾期跟进提醒
 */
@Service
@RequiredArgsConstructor
public class SaleFollowService {

    private final SaleMappers.FollowMapper followMapper;
    private final SaleSupport support;

    // ==================== 跟进记录 CRUD ====================

    public PageResult<SaleFollow> page(long current, long size, Long projectId, String dealStatus, String intentLevel, String kw) {
        LambdaQueryWrapper<SaleFollow> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleFollow::getProjectId, projectId);
        if (dealStatus != null && !dealStatus.isBlank()) qw.eq(SaleFollow::getDealStatus, dealStatus);
        if (intentLevel != null && !intentLevel.isBlank()) qw.eq(SaleFollow::getIntentLevel, intentLevel);
        if (kw != null && !kw.isBlank()) qw.like(SaleFollow::getCustomerName, kw);
        qw.orderByDesc(SaleFollow::getFollowAt);
        Page<SaleFollow> p = followMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleFollow detail(Long id) {
        SaleFollow f = require(id);
        fillDisplay(f);
        return f;
    }

    @Transactional
    public Long create(SaleFollow f) {
        if (f == null) throw new BizException("缺少跟进内容");
        if (f.getProjectId() == null) throw new BizException("请选择项目");
        if (f.getCustomerName() == null || f.getCustomerName().isBlank()) throw new BizException("请填写客户姓名/手机号");
        if (f.getDealStatus() == null || f.getDealStatus().isBlank()) f.setDealStatus("FOLLOWING");
        if (f.getFollowAt() == null) f.setFollowAt(LocalDateTime.now());
        f.setId(null);
        followMapper.insert(f);
        return f.getId();
    }

    @Transactional
    public void update(SaleFollow f) {
        if (f == null || f.getId() == null) throw new BizException("缺少跟进ID");
        SaleFollow db = require(f.getId());
        f.setFollowAt(db.getFollowAt()); // 保留原跟进时间
        if (followMapper.updateById(f) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        followMapper.deleteById(id);
    }

    // ==================== 客户池概览 + 逾期提醒 ====================

    /** 客户池 KPI：总量/跟进中/已成交/流失/高意向/逾期跟进 */
    public Map<String, Object> pool(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", projectId);
        m.put("total", followMapper.selectCount(base(projectId, null, null)));
        m.put("following", followMapper.selectCount(base(projectId, "FOLLOWING", null)));
        m.put("deal", followMapper.selectCount(base(projectId, "DEAL", null)));
        m.put("lost", followMapper.selectCount(base(projectId, "LOST", null)));
        m.put("highIntent", followMapper.selectCount(base(projectId, "FOLLOWING", "HIGH")));
        m.put("overdue", followMapper.selectCount(overdue(projectId)));
        return m;
    }

    /** 逾期跟进清单（跟进中 且 下次跟进时间已到期/逾期） */
    public List<SaleFollow> overdueList(Long projectId) {
        List<SaleFollow> list = followMapper.selectList(overdue(projectId == null ? null : projectId));
        list.forEach(this::fillDisplay);
        return list;
    }

    // ==================== 私有 ====================

    private LambdaQueryWrapper<SaleFollow> base(Long projectId, String dealStatus, String intentLevel) {
        LambdaQueryWrapper<SaleFollow> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleFollow::getProjectId, projectId);
        if (dealStatus != null && !dealStatus.isBlank()) qw.eq(SaleFollow::getDealStatus, dealStatus);
        if (intentLevel != null && !intentLevel.isBlank()) qw.eq(SaleFollow::getIntentLevel, intentLevel);
        return qw;
    }

    private LambdaQueryWrapper<SaleFollow> overdue(Long projectId) {
        LambdaQueryWrapper<SaleFollow> qw = base(projectId, "FOLLOWING", null);
        qw.le(SaleFollow::getNextFollowDate, LocalDate.now());
        return qw;
    }

    private SaleFollow require(Long id) {
        if (id == null) throw new BizException("缺少跟进ID");
        SaleFollow f = followMapper.selectById(id);
        if (f == null) throw new BizException("跟进记录不存在");
        return f;
    }

    private void fillDisplay(SaleFollow f) {
        f.setFollowByName(support.agentName(f.getFollowBy()));
    }
}
