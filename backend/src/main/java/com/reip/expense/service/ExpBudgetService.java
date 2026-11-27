package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.budget.service.BudControlService;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpBudget;
import com.reip.expense.mapper.ExpenseMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 费用预算：编制(部门×科目×月) / 四栏台账(预算/占用/核销/余额)
 * B4：编制/调整时校验同期月度费用预算（全面预算费用线）
 */
@Service
@RequiredArgsConstructor
public class ExpBudgetService {

    private final ExpenseMappers.BudgetMapper budgetMapper;
    private final ExpenseMappers.MdmLookupMapper mdm;
    private final BudControlService budControlService;

    public PageResult<ExpBudget> page(long current, long size, Long deptId, Integer year,
                                      String accountName, String status) {
        LambdaQueryWrapper<ExpBudget> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(ExpBudget::getDeptId, deptId);
        if (year != null) qw.eq(ExpBudget::getBudgetYear, year);
        if (accountName != null && !accountName.isBlank()) qw.like(ExpBudget::getAccountName, accountName);
        if (status != null && !status.isBlank()) qw.eq(ExpBudget::getStatus, status);
        qw.orderByAsc(ExpBudget::getDeptId).orderByAsc(ExpBudget::getAccountName);
        Page<ExpBudget> p = budgetMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillDisplay);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpBudget detail(Long id) {
        ExpBudget b = require(id);
        fillDisplay(b);
        return b;
    }

    @Transactional
    public Long create(ExpBudget b) {
        if (b == null) throw new BizException("缺少预算内容");
        if (b.getDeptId() == null) throw new BizException("请选择部门");
        if (mdm.selectOrgName(b.getDeptId()) == null) throw new BizException("部门不存在：" + b.getDeptId());
        if (b.getBudgetYear() == null) throw new BizException("请填写预算年度");
        ExpSupport.requireAmount(b.getAmount(), "预算金额");
        // B4 全面预算月度管控：费用预算(部门×科目×月)合计 ≤ 同期月度费用预算
        if (b.getAccountId() != null) {
            budControlService.checkExpenseBudget(b.getDeptId(), b.getAccountId(), b.getBudgetYear(),
                    b.getMonth(), b.getAmount(), null);
        }
        b.setId(null);
        if (b.getAmount().signum() > 0 && b.getOccupyAmount() == null) b.setOccupyAmount(java.math.BigDecimal.ZERO);
        if (b.getWriteoffAmount() == null) b.setWriteoffAmount(java.math.BigDecimal.ZERO);
        // 余额 = 预算 - 占用 - 核销
        b.setBalance(b.getAmount().subtract(ExpSupport.nvl(b.getOccupyAmount())).subtract(ExpSupport.nvl(b.getWriteoffAmount())));
        if (b.getStatus() == null || b.getStatus().isBlank()) b.setStatus("ACTIVE");
        budgetMapper.insert(b);
        return b.getId();
    }

    @Transactional
    public void update(ExpBudget b) {
        if (b == null || b.getId() == null) throw new BizException("缺少预算ID");
        ExpBudget db = require(b.getId());
        if (b.getAmount() == null) b.setAmount(db.getAmount());
        if (b.getOccupyAmount() == null) b.setOccupyAmount(db.getOccupyAmount());
        if (b.getWriteoffAmount() == null) b.setWriteoffAmount(db.getWriteoffAmount());
        // B4 仅当金额上调时校验（调减不新增占用额度）
        if (b.getAmount().compareTo(ExpSupport.nvl(db.getAmount())) > 0 && db.getAccountId() != null) {
            budControlService.checkExpenseBudget(db.getDeptId(), db.getAccountId(), db.getBudgetYear(),
                    db.getMonth(), b.getAmount(), db.getId());
        }
        b.setBalance(b.getAmount().subtract(ExpSupport.nvl(b.getOccupyAmount())).subtract(ExpSupport.nvl(b.getWriteoffAmount())));
        if (budgetMapper.updateById(b) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ExpBudget db = require(id);
        if (ExpSupport.nvl(db.getOccupyAmount()).signum() > 0 || ExpSupport.nvl(db.getWriteoffAmount()).signum() > 0)
            throw new BizException("该预算已存在占用/核销，不能删除");
        budgetMapper.deleteById(id);
    }

    /** 预算台账（四栏），供报表/前端总览 */
    public List<ExpBudget> ledger(Long deptId, Integer year) {
        LambdaQueryWrapper<ExpBudget> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(ExpBudget::getDeptId, deptId);
        if (year != null) qw.eq(ExpBudget::getBudgetYear, year);
        qw.orderByAsc(ExpBudget::getDeptId).orderByAsc(ExpBudget::getAccountName);
        List<ExpBudget> list = budgetMapper.selectList(qw);
        list.forEach(this::fillDisplay);
        return list;
    }

    private ExpBudget require(Long id) {
        if (id == null) throw new BizException("缺少预算ID");
        ExpBudget b = budgetMapper.selectById(id);
        if (b == null) throw new BizException("预算不存在");
        return b;
    }

    private void fillDisplay(ExpBudget b) {
        if (b.getDeptId() != null) b.setDeptName(mdm.selectOrgName(b.getDeptId()));
    }
}
