package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.sales.entity.SaleDiscount;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 价格管理：一房一价（表价/底价）维护 + 折扣方案（分级权限）
 */
@Service
@RequiredArgsConstructor
public class SalePriceService {

    private final SaleMappers.RoomStatusMapper roomMapper;
    private final SaleMappers.DiscountMapper discountMapper;
    private final SaleRoomService roomService;
    private final CodeRuleService codeRuleService;

    // ==================== 一房一价 ====================

    /** 修改房源表价/底价（一房一价） */
    @Transactional
    public void updatePrice(Long roomId, BigDecimal tablePrice, BigDecimal floorPrice) {
        if (tablePrice == null) throw new BizException("请填写表价");
        if (floorPrice != null && floorPrice.compareTo(tablePrice) > 0)
            throw new BizException("底价不得高于表价");
        SaleRoomStatus r = roomService.require(roomId);
        r.setTablePrice(tablePrice);
        if (floorPrice != null) r.setFloorPrice(floorPrice);
        if (roomMapper.updateById(r) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 折扣方案 ====================

    public PageResult<SaleDiscount> pageDiscounts(long current, long size, String level, String status) {
        LambdaQueryWrapper<SaleDiscount> qw = new LambdaQueryWrapper<>();
        if (level != null && !level.isBlank()) qw.eq(SaleDiscount::getLevel, level);
        if (status != null && !status.isBlank()) qw.eq(SaleDiscount::getStatus, status);
        qw.orderByAsc(SaleDiscount::getLevel).orderByDesc(SaleDiscount::getId);
        Page<SaleDiscount> p = discountMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleDiscount discountDetail(Long id) {
        SaleDiscount d = discountMapper.selectById(id);
        if (d == null) throw new BizException("折扣方案不存在");
        return d;
    }

    @Transactional
    public Long createDiscount(SaleDiscount d) {
        if (d == null) throw new BizException("缺少折扣方案内容");
        if (d.getName() == null || d.getName().isBlank()) throw new BizException("请填写方案名称");
        if (d.getLevel() == null || d.getLevel().isBlank()) throw new BizException("请选择级别(AGENT/MGR/GM)");
        if (d.getMaxRate() == null) throw new BizException("请填写最大可下探折扣率");
        if (d.getMaxRate().compareTo(BigDecimal.ONE) > 0 || d.getMaxRate().compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("折扣率须在 (0,1] 区间内");
        if (d.getBaseRate() != null && d.getMaxRate().compareTo(d.getBaseRate()) < 0)
            throw new BizException("最大可下探折扣率不得低于基准折扣率");
        d.setId(null);
        d.setDiscountNo(codeRuleService.next("ZK", ctx()));
        if (d.getStatus() == null || d.getStatus().isBlank()) d.setStatus("ACTIVE");
        discountMapper.insert(d);
        return d.getId();
    }

    @Transactional
    public void updateDiscount(SaleDiscount d) {
        if (d == null || d.getId() == null) throw new BizException("缺少折扣方案ID");
        SaleDiscount db = discountMapper.selectById(d.getId());
        if (db == null) throw new BizException("折扣方案不存在");
        d.setDiscountNo(null); // 编号不可改
        if (d.getMaxRate() != null && d.getBaseRate() != null && d.getMaxRate().compareTo(d.getBaseRate()) < 0)
            throw new BizException("最大可下探折扣率不得低于基准折扣率");
        if (discountMapper.updateById(d) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteDiscount(Long id) {
        discountDetail(id);
        discountMapper.deleteById(id);
    }

    /** 折扣方案编码上下文（折扣为集团级，不绑定项目，projectCode 取项目1） */
    private java.util.Map<String, String> ctx() {
        return java.util.Map.of("projectCode", "GRP");
    }
}
