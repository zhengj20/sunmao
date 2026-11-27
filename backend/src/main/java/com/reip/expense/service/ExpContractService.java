package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpContract;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.coding.CodeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 费用合同：登记 / 查询 / 状态维护（付款跟踪由实付登记回写）
 */
@Service
@RequiredArgsConstructor
public class ExpContractService {

    private final ExpenseMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;

    public PageResult<ExpContract> page(long current, long size, String status, String supplier, String kw) {
        LambdaQueryWrapper<ExpContract> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(ExpContract::getStatus, status);
        if (supplier != null && !supplier.isBlank()) qw.like(ExpContract::getSupplier, supplier);
        if (kw != null && !kw.isBlank())
            qw.and(w -> w.like(ExpContract::getContractNo, kw).or().like(ExpContract::getName, kw));
        qw.orderByDesc(ExpContract::getId);
        Page<ExpContract> p = contractMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 付款单关联下拉：生效中合同 */
    public List<ExpContract> options() {
        return contractMapper.selectList(new LambdaQueryWrapper<ExpContract>()
                .eq(ExpContract::getStatus, "ACTIVE")
                .orderByDesc(ExpContract::getId));
    }

    public ExpContract detail(Long id) {
        ExpContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException("合同不存在");
        return c;
    }

    @Transactional
    public Long create(ExpContract c) {
        if (c == null) throw new BizException("缺少合同内容");
        if (c.getName() == null || c.getName().isBlank()) throw new BizException("请填写合同名称");
        ExpSupport.requireAmount(c.getAmount(), "合同金额");
        validateDates(c);
        c.setId(null);
        c.setContractNo(codeRuleService.next("FKH", Map.of()));
        c.setPaidAmount(BigDecimal.ZERO);
        c.setStatus("ACTIVE");
        contractMapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void update(ExpContract c) {
        if (c == null || c.getId() == null) throw new BizException("缺少合同ID");
        ExpContract db = detail(c.getId());
        if (c.getAmount() != null && c.getAmount().compareTo(ExpSupport.nvl(db.getPaidAmount())) < 0)
            throw new BizException("合同金额不能小于已付金额 " + db.getPaidAmount());
        validateDates(c);
        c.setContractNo(null);
        c.setPaidAmount(null);
        if (contractMapper.updateById(c) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 状态维护：终止/恢复/到期 */
    @Transactional
    public void changeStatus(Long id, String status) {
        if (!List.of("ACTIVE", "EXPIRED", "TERMINATED").contains(status))
            throw new BizException("非法状态：" + status);
        ExpContract c = new ExpContract();
        c.setId(id);
        c.setStatus(status);
        if (contractMapper.updateById(c) == 0) throw new BizException("合同不存在或数据已被他人修改");
    }

    @Transactional
    public void delete(Long id) {
        ExpContract db = detail(id);
        if (ExpSupport.nvl(db.getPaidAmount()).signum() > 0)
            throw new BizException("该合同已有付款记录，不能删除");
        contractMapper.deleteById(id);
    }

    private static void validateDates(ExpContract c) {
        if (c.getStartDate() != null && c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate()))
            throw new BizException("到期日期不能早于生效日期");
    }
}
