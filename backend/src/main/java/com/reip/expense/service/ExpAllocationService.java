package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpAllocation;
import com.reip.expense.mapper.ExpenseMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 费用分摊：规则配置 / 月度分摊计算
 */
@Service
@RequiredArgsConstructor
public class ExpAllocationService {

    private final ExpenseMappers.AllocationMapper allocMapper;
    private final ExpenseMappers.MdmLookupMapper mdm;

    public PageResult<ExpAllocation> page(long current, long size, String month, Long deptId, Long projectId) {
        LambdaQueryWrapper<ExpAllocation> qw = new LambdaQueryWrapper<>();
        if (month != null && !month.isBlank()) qw.eq(ExpAllocation::getMonth, month);
        if (deptId != null) qw.eq(ExpAllocation::getDeptId, deptId);
        if (projectId != null) qw.eq(ExpAllocation::getProjectId, projectId);
        qw.orderByDesc(ExpAllocation::getId);
        Page<ExpAllocation> p = allocMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpAllocation detail(Long id) {
        ExpAllocation a = allocMapper.selectById(id);
        if (a == null) throw new BizException("分摊记录不存在");
        return a;
    }

    /** 分摊规则配置（单条） */
    @Transactional
    public Long create(ExpAllocation a) {
        if (a == null) throw new BizException("缺少分摊内容");
        if (a.getMonth() == null || a.getMonth().isBlank()) throw new BizException("请填写分摊月(yyyy-MM)");
        if (a.getDeptId() == null) throw new BizException("请选择分摊目标部门");
        ExpSupport.requireAmount(a.getAmount(), "分摊金额");
        if (mdm.selectOrgName(a.getDeptId()) == null) throw new BizException("部门不存在：" + a.getDeptId());
        a.setId(null);
        if (a.getAllocNo() == null || a.getAllocNo().isBlank()) a.setAllocNo("ALLOC-" + a.getMonth() + "-" + a.getDeptId());
        allocMapper.insert(a);
        return a.getId();
    }

    @Transactional
    public void delete(Long id) {
        if (allocMapper.selectById(id) == null) throw new BizException("分摊记录不存在");
        allocMapper.deleteById(id);
    }

    /**
     * 月度分摊计算：将总金额按分摊目标均摊（尾差归最后一个目标），生成多条分摊记录。
     * 规则说明写入 rule_text。
     */
    @Transactional
    public int calculate(String month, BigDecimal total, List<Long> deptIds) {
        if (month == null || month.isBlank()) throw new BizException("请填写分摊月(yyyy-MM)");
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写分摊总额");
        if (deptIds == null || deptIds.isEmpty()) throw new BizException("请选择分摊目标部门");
        List<Long> targets = deptIds.stream().distinct().toList();
        int n = targets.size();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal assigned = BigDecimal.ZERO;
        int idx = 0;
        for (Long dept : targets) {
            if (mdm.selectOrgName(dept) == null) throw new BizException("部门不存在：" + dept);
            idx++;
            BigDecimal amt = (idx == n) ? total.subtract(assigned) : base;
            assigned = assigned.add(amt);
            ExpAllocation a = new ExpAllocation();
            a.setMonth(month);
            a.setDeptId(dept);
            a.setAmount(amt);
            a.setRuleText("均摊规则：总额 " + total + " / " + n + " 个目标，尾差归末位");
            a.setAllocNo("ALLOC-" + month + "-" + dept);
            allocMapper.insert(a);
        }
        return n;
    }
}
