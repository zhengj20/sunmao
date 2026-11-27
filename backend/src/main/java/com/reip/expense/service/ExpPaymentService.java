package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpContract;
import com.reip.expense.entity.ExpPayment;
import com.reip.expense.entity.ExpReimburse;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报销付款单：生成 / 实付登记(回写报销 PAID)
 */
@Service
@RequiredArgsConstructor
public class ExpPaymentService {

    private final ExpenseMappers.PaymentMapper payMapper;
    private final ExpenseMappers.ReimburseMapper reimbMapper;
    private final ExpenseMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;
    private final EventPublisher eventPublisher;

    public PageResult<ExpPayment> page(long current, long size, Long reimbId, String status, String kw) {
        LambdaQueryWrapper<ExpPayment> qw = new LambdaQueryWrapper<>();
        if (reimbId != null) qw.eq(ExpPayment::getReimbId, reimbId);
        if (status != null && !status.isBlank()) qw.eq(ExpPayment::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(ExpPayment::getPayNo, kw);
        qw.orderByDesc(ExpPayment::getId);
        Page<ExpPayment> p = payMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpPayment detail(Long id) {
        ExpPayment p = payMapper.selectById(id);
        if (p == null) throw new BizException("付款单不存在");
        return p;
    }

    /** 为审批通过的报销生成付款单（可选关联费用合同，实付时回写合同已付） */
    @Transactional
    public Long create(Long reimbId, BigDecimal amount, Long contractId) {
        if (reimbId == null) throw new BizException("请选择报销单");
        ExpReimburse r = reimbMapper.selectById(reimbId);
        if (r == null) throw new BizException("报销单不存在");
        if (!"APPROVED".equals(r.getStatus()) && !"PAID".equals(r.getStatus()))
            throw new BizException("仅审批通过的报销单可生成付款单，当前：" + r.getStatus());
        // 已有 DRAFT/PAID 付款单则不重复生成
        Long exist = payMapper.selectCount(new LambdaQueryWrapper<ExpPayment>()
                .eq(ExpPayment::getReimbId, reimbId).ne(ExpPayment::getStatus, "VOID"));
        if (exist != null && exist > 0) throw new BizException("该报销单已存在付款单");
        BigDecimal amt = amount == null ? r.getTotalAmount() : amount;
        ExpSupport.requireAmount(amt, "付款金额");
        if (contractId != null) {
            ExpContract c = contractMapper.selectById(contractId);
            if (c == null) throw new BizException("费用合同不存在");
            if (!"ACTIVE".equals(c.getStatus())) throw new BizException("仅生效中的费用合同可关联付款");
            BigDecimal remaining = c.getAmount().subtract(ExpSupport.nvl(c.getPaidAmount()));
            if (amt.compareTo(remaining) > 0) throw new BizException("付款金额超过合同未付余额 " + remaining);
        }
        ExpPayment p = new ExpPayment();
        p.setReimbId(reimbId);
        p.setContractId(contractId);
        p.setAmount(amt);
        p.setPayNo(codeRuleService.next("FKP", Map.of()));
        p.setStatus("DRAFT");
        payMapper.insert(p);
        return p.getId();
    }

    /** 纯合同付款：为生效中的费用合同直接生成付款单（不关联报销单） */
    @Transactional
    public Long createForContract(Long contractId, BigDecimal amount) {
        ExpContract c = contractMapper.selectById(contractId);
        if (c == null) throw new BizException("费用合同不存在");
        if (!"ACTIVE".equals(c.getStatus())) throw new BizException("仅生效中的费用合同可付款");
        BigDecimal amt = amount == null ? c.getAmount().subtract(ExpSupport.nvl(c.getPaidAmount())) : amount;
        ExpSupport.requireAmount(amt, "付款金额");
        BigDecimal remaining = c.getAmount().subtract(ExpSupport.nvl(c.getPaidAmount()));
        if (amt.compareTo(remaining) > 0) throw new BizException("付款金额超过合同未付余额 " + remaining);
        ExpPayment p = new ExpPayment();
        p.setContractId(contractId);
        p.setAmount(amt);
        p.setPayNo(codeRuleService.next("FKP", Map.of()));
        p.setStatus("DRAFT");
        payMapper.insert(p);
        return p.getId();
    }

    /** 某合同的付款记录（新到旧） */
    public List<ExpPayment> listByContract(Long contractId) {
        LambdaQueryWrapper<ExpPayment> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpPayment::getContractId, contractId).ne(ExpPayment::getStatus, "VOID");
        qw.orderByDesc(ExpPayment::getId);
        return payMapper.selectList(qw);
    }

    /** 实付登记：付款单 PAID + 报销单回写 PAID + 发布事件 */
    @Transactional
    public void pay(Long id, BigDecimal paidAmount, LocalDate paidDate) {
        ExpPayment p = detail(id);
        if (!"DRAFT".equals(p.getStatus())) throw new BizException("仅草稿付款单可登记实付，当前：" + p.getStatus());
        BigDecimal amt = paidAmount == null ? p.getAmount() : paidAmount;
        if (amt.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写实付金额");
        p.setStatus("PAID");
        p.setAmount(amt);
        p.setPaidDate(paidDate == null ? LocalDate.now() : paidDate);
        if (payMapper.updateById(p) == 0) throw new BizException("数据已被他人修改，请刷新后重试");

        // 回写关联费用合同已付金额（原子累加，超合同额阻断）
        if (p.getContractId() != null) {
            int n = contractMapper.addPaid(p.getContractId(), amt);
            if (n == 0) throw new BizException("合同未付余额不足，无法回写已付金额");
        }

        // 回写报销单 PAID（纯合同付款无报销单）
        if (p.getReimbId() != null) {
        ExpReimburse r = reimbMapper.selectById(p.getReimbId());
        if (r != null && "APPROVED".equals(r.getStatus())) {
            ExpReimburse up = new ExpReimburse();
            up.setId(r.getId());
            up.setVersion(r.getVersion());
            up.setStatus("PAID");
            reimbMapper.updateById(up);
        }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reimbId", p.getReimbId());
        payload.put("paymentId", p.getId());
        payload.put("amount", amt);
        payload.put("paidDate", p.getPaidDate());
        eventPublisher.publish("expense.payment.paid", "EXP_PAYMENT", p.getId(), payload);
    }
}
