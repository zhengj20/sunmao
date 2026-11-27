package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.sales.entity.SaleChannel;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售渠道：中介/分销/线上/老带新/自然 —— 渠道主数据 CRUD
 * （渠道带客成交分析见 SaleDashboardService.channelStructure）
 */
@Service
@RequiredArgsConstructor
public class SaleChannelService {

    private final SaleMappers.ChannelMapper channelMapper;

    public PageResult<SaleChannel> page(long current, long size, Long projectId, String channelType, String status) {
        LambdaQueryWrapper<SaleChannel> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleChannel::getProjectId, projectId);
        if (channelType != null && !channelType.isBlank()) qw.eq(SaleChannel::getChannelType, channelType);
        if (status != null && !status.isBlank()) qw.eq(SaleChannel::getStatus, status);
        qw.orderByDesc(SaleChannel::getId);
        Page<SaleChannel> p = channelMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleChannel detail(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(SaleChannel c) {
        if (c == null) throw new BizException("缺少渠道");
        if (c.getProjectId() == null) throw new BizException("请选择项目");
        if (c.getChannelName() == null || c.getChannelName().isBlank()) throw new BizException("请填写渠道名称");
        if (c.getStatus() == null || c.getStatus().isBlank()) c.setStatus("ACTIVE");
        c.setId(null);
        channelMapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void update(SaleChannel c) {
        if (c == null || c.getId() == null) throw new BizException("缺少渠道ID");
        require(c.getId());
        if (channelMapper.updateById(c) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        channelMapper.deleteById(id);
    }

    private SaleChannel require(Long id) {
        if (id == null) throw new BizException("缺少渠道ID");
        SaleChannel c = channelMapper.selectById(id);
        if (c == null) throw new BizException("渠道不存在");
        return c;
    }
}
