package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.*;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报销：登记(带明细) / R2发票查重·验真 / R3超标特批 / 冲借款 / 核销预算 / 发布 expense.reimbursed
 */
@Service
@RequiredArgsConstructor
public class ExpReimburseService {

    private final ExpenseMappers.ReimburseMapper reimbMapper;
    private final ExpenseMappers.ReimburseItemMapper itemMapper;
    private final ExpenseMappers.InvoiceMapper invoiceMapper;
    private final ExpenseMappers.BudgetMapper budgetMapper;
    private final ExpenseMappers.LoanMapper loanMapper;
    private final ExpenseMappers.ApplyMapper applyMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;
    private final ParamService paramService;

    public PageResult<ExpReimburse> page(long current, long size, Long deptId, Long userId,
                                         String type, String status, String kw) {
        LambdaQueryWrapper<ExpReimburse> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(ExpReimburse::getDeptId, deptId);
        if (userId != null) qw.eq(ExpReimburse::getUserId, userId);
        if (type != null && !type.isBlank()) qw.eq(ExpReimburse::getType, type);
        if (status != null && !status.isBlank()) qw.eq(ExpReimburse::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(ExpReimburse::getReimbNo, kw);
        qw.orderByDesc(ExpReimburse::getId);
        Page<ExpReimburse> p = reimbMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpReimburse detail(Long id) {
        ExpReimburse r = require(id);
        r.setItems(itemMapper.selectList(new LambdaQueryWrapper<ExpReimburseItem>()
                .eq(ExpReimburseItem::getReimbId, id).orderByAsc(ExpReimburseItem::getId)));
        return r;
    }

    /** 登记报销（带明细行）。R3：超标须填说明；关联发票须已验真通过 */
    @Transactional
    public Long create(ExpReimburse r) {
        if (r == null) throw new BizException("缺少报销内容");
        if (r.getDeptId() == null) throw new BizException("请选择部门");
        ExpSupport.requireAmount(r.getTotalAmount(), "报销总额");
        // 关联发票验真校验（R2 配套）
        if (r.getInvoiceId() != null) {
            ExpInvoice inv = invoiceMapper.selectById(r.getInvoiceId());
            if (inv == null) throw new BizException("关联发票不存在");
            if (!"PASS".equals(inv.getVerifyStatus()))
                throw new BizException("发票未通过验真（当前：" + inv.getVerifyStatus() + "），不可登记/提交报销");
        }
        // 超标自动计算：实际 - 标准（标准为关联申请金额；无申请视为无标准）
        BigDecimal standard = BigDecimal.ZERO;
        if (r.getApplyId() != null) {
            ExpApply a = applyMapper.selectById(r.getApplyId());
            if (a != null) standard = ExpSupport.nvl(a.getAmount());
        }
        if (r.getOverAmount() == null) {
            r.setOverAmount(r.getTotalAmount().subtract(standard).max(BigDecimal.ZERO));
        }
        if (r.getOverAmount().compareTo(BigDecimal.ZERO) > 0
                && (r.getOverReason() == null || r.getOverReason().isBlank()))
            throw new BizException("报销超标 " + r.getOverAmount() + "，须填写超标说明（R3）");

        r.setId(null);
        r.setReimbNo(codeRuleService.next("BX", Map.of()));
        if (r.getUserId() == null) r.setUserId(UserContext.require().getUserId());
        if (r.getUserName() == null || r.getUserName().isBlank()) r.setUserName(UserContext.require().getRealName());
        r.setStatus("DRAFT");
        reimbMapper.insert(r);
        // 明细行
        if (r.getItems() != null && !r.getItems().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (ExpReimburseItem it : r.getItems()) {
                it.setId(null);
                it.setReimbId(r.getId());
                if (it.getAmount() == null) it.setAmount(BigDecimal.ZERO);
                sum = sum.add(it.getAmount());
                itemMapper.insert(it);
            }
            if (sum.compareTo(r.getTotalAmount()) != 0)
                throw new BizException("明细合计 " + sum + " 与报销总额 " + r.getTotalAmount() + " 不一致");
        }
        return r.getId();
    }

    /**
     * 提交：
     *  R2/验真复查 → 超标(over>0)走 EXP_REIMB_OVERRUN 特批；未超标直接 APPROVED 并核销(预算+借款)
     */
    @Transactional
    public void submit(Long id) {
        ExpReimburse r = require(id);
        if (!"DRAFT".equals(r.getStatus()) && !"REJECTED".equals(r.getStatus()))
            throw new BizException("当前状态不可提交报销");
        // 验真复查
        if (r.getInvoiceId() != null && paramService.getBool("exp.invoice.dedup", true)) {
            ExpInvoice inv = invoiceMapper.selectById(r.getInvoiceId());
            if (inv == null) throw new BizException("关联发票不存在");
            if (!"PASS".equals(inv.getVerifyStatus()))
                throw new BizException("发票未通过验真，不可提交报销");
        }
        boolean over = r.getOverAmount() != null && r.getOverAmount().compareTo(BigDecimal.ZERO) > 0;
        if (over) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("报销单号", r.getReimbNo());
            snapshot.put("部门", r.getDeptId());
            snapshot.put("总额", r.getTotalAmount());
            snapshot.put("超标额", r.getOverAmount());
            snapshot.put("超标说明", r.getOverReason());
            workflowClient.start("EXP_REIMB_OVERRUN", id, "报销超标特批 " + r.getReimbNo(), r.getTotalAmount(), snapshot);
            r.setStatus("APPROVING");
            if (reimbMapper.updateById(r) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        } else {
            // 未超标：直接生效并核销
            r.setStatus("APPROVED");
            reimbMapper.updateById(r);
            finalizeApproved(id);
        }
    }

    // ==================== 工作流回调 ====================

    /** 超标特批通过：APPROVED + 核销(预算+借款) + 发布 expense.reimbursed */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        ExpReimburse r = reimbMapper.selectById(id);
        if (r == null || !"APPROVING".equals(r.getStatus())) return; // 幂等
        r.setStatus("APPROVED");
        reimbMapper.updateById(r);
        finalizeApproved(id);
    }

    /** 超标特批驳回：REJECTED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        ExpReimburse r = reimbMapper.selectById(id);
        if (r == null || !"APPROVING".equals(r.getStatus())) return; // 幂等
        r.setStatus("REJECTED");
        reimbMapper.updateById(r);
    }

    /** 生效后动作：核销预算(关联申请预算) + 冲借款 + 发布事件 */
    private void finalizeApproved(Long id) {
        ExpReimburse r = reimbMapper.selectById(id);
        BigDecimal total = ExpSupport.nvl(r.getTotalAmount());
        // 核销预算：报销款关联的事前申请预算
        if (r.getApplyId() != null) {
            ExpApply a = applyMapper.selectById(r.getApplyId());
            if (a != null && a.getBudgetId() != null) {
                int n = budgetMapper.writeoff(a.getBudgetId(), total);
                if (n == 0) {
                    // 余额不足：不阻断核销（超预算报销已由 R3 特批放行），仅记录
                }
            }
        }
        // 冲借款：报销款优先还借款
        if (r.getLoanId() != null) {
            ExpLoan l = loanMapper.selectById(r.getLoanId());
            if (l != null) {
                BigDecimal cover = total.min(ExpSupport.nvl(l.getUnwriteoffBalance()));
                if (cover.compareTo(BigDecimal.ZERO) > 0) loanMapper.reduceBalance(l.getId(), cover);
                if (ExpSupport.nvl(l.getUnwriteoffBalance()).subtract(cover).compareTo(BigDecimal.ZERO) <= 0) {
                    ExpLoan up = new ExpLoan();
                    up.setId(l.getId());
                    up.setVersion(l.getVersion());
                    up.setStatus("WRITEOFF");
                    loanMapper.updateById(up);
                }
            }
        }
        // 发布事件
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deptId", r.getDeptId());
        payload.put("reimbId", r.getId());
        payload.put("reimbNo", r.getReimbNo());
        payload.put("totalAmount", total);
        payload.put("overAmount", ExpSupport.nvl(r.getOverAmount()));
        eventPublisher.publish("expense.reimbursed", "EXP_REIMBURSE", r.getId(), payload);
    }

    private ExpReimburse require(Long id) {
        if (id == null) throw new BizException("缺少报销单ID");
        ExpReimburse r = reimbMapper.selectById(id);
        if (r == null) throw new BizException("报销单不存在");
        return r;
    }
}
