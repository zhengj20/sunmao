package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpStandard;
import com.reip.expense.mapper.ExpenseMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 费控标准库维护（职级×城市×类型）
 */
@Service
@RequiredArgsConstructor
public class ExpStandardService {

    private final ExpenseMappers.StandardMapper stdMapper;

    public PageResult<ExpStandard> page(long current, long size, String level, String cityLevel, String type) {
        LambdaQueryWrapper<ExpStandard> qw = new LambdaQueryWrapper<>();
        if (level != null && !level.isBlank()) qw.eq(ExpStandard::getLevel, level);
        if (cityLevel != null && !cityLevel.isBlank()) qw.eq(ExpStandard::getCityLevel, cityLevel);
        if (type != null && !type.isBlank()) qw.eq(ExpStandard::getType, type);
        qw.orderByAsc(ExpStandard::getLevel).orderByAsc(ExpStandard::getType);
        Page<ExpStandard> p = stdMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpStandard detail(Long id) {
        ExpStandard s = stdMapper.selectById(id);
        if (s == null) throw new BizException("费控标准不存在");
        return s;
    }

    @Transactional
    public Long create(ExpStandard s) {
        if (s == null) throw new BizException("缺少标准内容");
        if (s.getLevel() == null || s.getLevel().isBlank()) throw new BizException("请选择职级");
        if (s.getCityLevel() == null || s.getCityLevel().isBlank()) throw new BizException("请选择城市等级");
        if (s.getType() == null || s.getType().isBlank()) throw new BizException("请选择类型(TRAVEL/HOST/COMMS/CAR)");
        ExpSupport.requireAmount(s.getLimitAmount(), "限额标准");
        s.setId(null);
        stdMapper.insert(s);
        return s.getId();
    }

    @Transactional
    public void update(ExpStandard s) {
        if (s == null || s.getId() == null) throw new BizException("缺少标准ID");
        if (s.getLimitAmount() != null && s.getLimitAmount().signum() < 0) throw new BizException("限额标准不能为负");
        if (stdMapper.updateById(s) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        if (stdMapper.selectById(id) == null) throw new BizException("费控标准不存在");
        stdMapper.deleteById(id);
    }

    /** 取某职级+类型的最高限额标准（R3 超标计算基准，缺省视为 0） */
    public BigDecimal limitOf(String level, String type) {
        if (level == null || type == null) return BigDecimal.ZERO;
        ExpStandard s = stdMapper.selectOne(new LambdaQueryWrapper<ExpStandard>()
                .eq(ExpStandard::getLevel, level)
                .eq(ExpStandard::getType, type)
                .orderByDesc(ExpStandard::getLimitAmount)
                .last("LIMIT 1"));
        return s == null ? BigDecimal.ZERO : s.getLimitAmount();
    }
}
