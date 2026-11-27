package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpLoan;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 借款：登记+审批发起 / R4 额度控制(未冲销阻断 + 额度上限) / 冲销
 */
@Service
@RequiredArgsConstructor
public class ExpLoanService {

    private final ExpenseMappers.LoanMapper loanMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final ParamService paramService;

    public PageResult<ExpLoan> page(long current, long size, Long userId, String status, String kw) {
        LambdaQueryWrapper<ExpLoan> qw = new LambdaQueryWrapper<>();
        if (userId != null) qw.eq(ExpLoan::getUserId, userId);
        if (status != null && !status.isBlank()) qw.eq(ExpLoan::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(ExpLoan::getLoanNo, kw);
        qw.orderByDesc(ExpLoan::getId);
        Page<ExpLoan> p = loanMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpLoan detail(Long id) {
        return require(id);
    }

    /**
     * 登记并发起借款审批（bizType=EXP_LOAN）。
     * R4：存在未冲销借款则阻断（逾期未冲销给出专项提示）；未冲销余额+本次 ≤ 额度上限。
     */
    @Transactional
    public Long create(ExpLoan l) {
        if (l == null) throw new BizException("缺少借款内容");
        ExpSupport.requireAmount(l.getAmount(), "借款金额");
        if (l.getUserId() == null) l.setUserId(UserContext.require().getUserId());
        if (l.getUserName() == null || l.getUserName().isBlank()) l.setUserName(UserContext.require().getRealName());
        Long uid = l.getUserId();

        // R4-1 逾期未冲销阻断（专项提示）
        if (paramService.getBool("exp.loan.due_block", true) && loanMapper.countDueUnwriteoff(uid) > 0)
            throw new BizException("存在逾期未冲销借款，须先冲销/还款后再借（R4）");
        // R4-2 未冲销余额阻断：同一借款人存在未冲销借款时不可再借
        BigDecimal outstanding = loanMapper.sumUnwriteoff(uid);
        if (outstanding.compareTo(BigDecimal.ZERO) > 0)
            throw new BizException("存在未冲销借款（未冲销余额 " + outstanding + "），须先冲销/还款后再借（R4）");
        // R4-3 额度上限（职级上限以全局参数承载）
        BigDecimal limit = paramService.getDecimal("exp.loan.max_balance", new BigDecimal("100000"));
        if (outstanding.add(l.getAmount()).compareTo(limit) > 0)
            throw new BizException("借款超出额度上限 " + limit + "（未冲销 " + outstanding + " + 本次 " + l.getAmount() + "）");

        l.setId(null);
        l.setLoanNo(codeRuleService.next("JK", Map.of()));
        l.setUnwriteoffBalance(l.getAmount());
        if (l.getBorrowDate() == null) l.setBorrowDate(LocalDate.now());
        l.setStatus("APPROVING");
        loanMapper.insert(l);
        // 落库拿到 id 后发起审批流程
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("借据号", l.getLoanNo());
        snapshot.put("借款人", l.getUserName());
        snapshot.put("金额", l.getAmount());
        workflowClient.start("EXP_LOAN", l.getId(), "借款 " + l.getLoanNo(), l.getAmount(), snapshot);
        return l.getId();
    }

    // ==================== 工作流回调 ====================

    /** 借款审批通过：ACTIVE（未冲销余额生效） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        ExpLoan l = loanMapper.selectById(id);
        if (l == null || !"APPROVING".equals(l.getStatus())) return; // 幂等
        l.setStatus("ACTIVE");
        loanMapper.updateById(l);
    }

    /** 借款审批驳回：作废（未冲销余额清零，避免占用额度） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        ExpLoan l = loanMapper.selectById(id);
        if (l == null || !"APPROVING".equals(l.getStatus())) return; // 幂等
        l.setStatus("WRITEOFF");
        l.setUnwriteoffBalance(BigDecimal.ZERO);
        loanMapper.updateById(l);
    }

    /** 冲销/还款：未冲销余额扣减，清零后置 WRITEOFF */
    @Transactional
    public void writeoff(Long id, BigDecimal amount) {
        ExpLoan l = require(id);
        if (!"ACTIVE".equals(l.getStatus()) && !"APPROVING".equals(l.getStatus()))
            throw new BizException("仅审批中/生效借款可冲销，当前：" + l.getStatus());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写冲销金额");
        BigDecimal cur = ExpSupport.nvl(l.getUnwriteoffBalance());
        if (amount.compareTo(cur) > 0) throw new BizException("冲销金额不能超过未冲销余额 " + cur);
        loanMapper.reduceBalance(id, amount);
        if (cur.subtract(amount).compareTo(BigDecimal.ZERO) <= 0) {
            l.setStatus("WRITEOFF");
        }
        if (loanMapper.updateById(l) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    private ExpLoan require(Long id) {
        if (id == null) throw new BizException("缺少借据ID");
        ExpLoan l = loanMapper.selectById(id);
        if (l == null) throw new BizException("借据不存在");
        return l;
    }
}
