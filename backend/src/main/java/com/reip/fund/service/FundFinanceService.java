package com.reip.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.fund.entity.FundFinance;
import com.reip.fund.mapper.FundMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 融资台账
 */
@Service
@RequiredArgsConstructor
public class FundFinanceService {

    private final FundMappers.FinanceMapper financeMapper;

    public PageResult<FundFinance> page(long current, long size, Long projectId, String financeType, String status) {
        LambdaQueryWrapper<FundFinance> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(FundFinance::getProjectId, projectId);
        if (financeType != null && !financeType.isBlank()) qw.eq(FundFinance::getFinanceType, financeType);
        if (status != null && !status.isBlank()) qw.eq(FundFinance::getStatus, status);
        qw.orderByAsc(FundFinance::getDueDate).orderByDesc(FundFinance::getId);
        Page<FundFinance> p = financeMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public FundFinance detail(Long id) {
        FundFinance f = financeMapper.selectById(id);
        if (f == null) throw new BizException("融资记录不存在");
        return f;
    }

    @Transactional
    public Long create(FundFinance finance) {
        finance.setId(null);
        if (finance.getStatus() == null || finance.getStatus().isBlank()) finance.setStatus("NORMAL");
        if (finance.getUsedAmount() == null) finance.setUsedAmount(BigDecimal.ZERO);
        financeMapper.insert(finance);
        return finance.getId();
    }

    @Transactional
    public void update(FundFinance finance) {
        detail(finance.getId());
        int updated = financeMapper.updateById(finance);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        financeMapper.deleteById(id);
    }

    /** 到期提醒：due_date 在 days 天内的融资 */
    public List<FundFinance> expiring(int days) {
        LocalDate today = LocalDate.now();
        return financeMapper.selectList(new LambdaQueryWrapper<FundFinance>()
                .between(FundFinance::getDueDate, today, today.plusDays(days))
                .orderByAsc(FundFinance::getDueDate));
    }
}
