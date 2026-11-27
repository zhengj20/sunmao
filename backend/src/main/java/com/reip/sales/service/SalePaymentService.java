package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.sales.entity.SaleContract;
import com.reip.sales.entity.SaleMortgage;
import com.reip.sales.entity.SalePaymentPlan;
import com.reip.sales.entity.SaleReceipt;
import com.reip.sales.mapper.SaleMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回款：按付款方式生成应收计划 / 实收登记(核销应收 R5) / 按揭跟踪 / 逾期清单
 */
@Service
@RequiredArgsConstructor
public class SalePaymentService {

    private final SaleMappers.PaymentPlanMapper planMapper;
    private final SaleMappers.ReceiptMapper receiptMapper;
    private final SaleMappers.MortgageMapper mortgageMapper;
    private final SaleMappers.ContractMapper contractMapper;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;

    // ==================== 应收计划 ====================

    /** 按付款方式生成应收计划：ONE_TIME 1期 / MORTGAGE 2期 / INSTALLMENT 3期 */
    @Transactional
    public int generatePlans(Long contractId) {
        SaleContract c = requireContract(contractId);
        BigDecimal total = c.getContractPrice();
        LocalDate base = c.getSignedAt() == null ? LocalDate.now() : c.getSignedAt();
        // 幂等：已有应收则不重复生成
        Long exists = planMapper.selectCount(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId));
        if (exists != null && exists > 0) return 0;

        String method = c.getPayMethod() == null ? "MORTGAGE" : c.getPayMethod();
        List<PlanDef> defs = switch (method) {
            case "ONE_TIME" -> List.of(new PlanDef("签约款(一次性100%)", "1", base));
            case "INSTALLMENT" -> List.of(
                    new PlanDef("签约款(30%)", "0.30", base),
                    new PlanDef("分期款(40%)", "0.40", base.plusDays(60)),
                    new PlanDef("交房款(30%)", "0.30", base.plusDays(180)));
            default -> List.of( // MORTGAGE
                    new PlanDef("签约款(30%)", "0.30", base),
                    new PlanDef("按揭款(70%)", "0.70", base.plusDays(120)));
        };
        int seq = 0;
        for (PlanDef d : defs) {
            seq++;
            SalePaymentPlan p = new SalePaymentPlan();
            p.setContractId(contractId);
            p.setSeq(seq);
            p.setTermDesc(d.desc);
            p.setPlanAmount(total.multiply(new BigDecimal(d.ratio)).setScale(2, RoundingMode.HALF_UP));
            p.setReceivedAmount(BigDecimal.ZERO);
            p.setDueDate(d.due);
            p.setStatus("DUE");
            planMapper.insert(p);
        }
        return defs.size();
    }

    public List<SalePaymentPlan> plansByContract(Long contractId) {
        requireContract(contractId);
        List<SalePaymentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId)
                .orderByAsc(SalePaymentPlan::getSeq));
        plans.forEach(p -> p.setContractNo(contractNo(contractId)));
        return plans;
    }

    public PageResult<SalePaymentPlan> pagePlans(long current, long size, Long contractId, String status) {
        LambdaQueryWrapper<SalePaymentPlan> qw = new LambdaQueryWrapper<>();
        if (contractId != null) qw.eq(SalePaymentPlan::getContractId, contractId);
        if (status != null && !status.isBlank()) qw.eq(SalePaymentPlan::getStatus, status);
        qw.orderByAsc(SalePaymentPlan::getDueDate);
        Page<SalePaymentPlan> p = planMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillPlan);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 逾期清单：到期未收且未逾期标记的应收 */
    public List<SalePaymentPlan> overdue(Long projectId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<SalePaymentPlan> qw = new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getStatus, "DUE")
                .lt(SalePaymentPlan::getDueDate, today)
                .orderByAsc(SalePaymentPlan::getDueDate);
        List<SalePaymentPlan> plans = planMapper.selectList(qw);
        if (projectId != null) plans = plans.stream()
                .filter(p -> {
                    SaleContract c = contractMapper.selectById(p.getContractId());
                    return c != null && projectId.equals(c.getProjectId());
                }).toList();
        // 标记逾期
        for (SalePaymentPlan p : plans) {
            if (!"OVERDUE".equals(p.getStatus())) {
                p.setStatus("OVERDUE");
                planMapper.updateById(p);
            }
        }
        plans.forEach(this::fillPlan);
        return plans;
    }

    // ==================== 实收登记（核销 R5） ====================

    public PageResult<SaleReceipt> pageReceipts(long current, long size, Long contractId) {
        LambdaQueryWrapper<SaleReceipt> qw = new LambdaQueryWrapper<>();
        if (contractId != null) qw.eq(SaleReceipt::getContractId, contractId);
        qw.orderByDesc(SaleReceipt::getId);
        Page<SaleReceipt> p = receiptMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillReceipt);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 实收登记：创建实收单 + 核销应收（R5），更新回款率 */
    @Transactional
    public Long receive(Long contractId, ReceiveForm form) {
        SaleContract c = requireContract(contractId);
        if (!"ACTIVE".equals(c.getStatus()) && !"FILED".equals(c.getStatus()))
            throw new BizException("仅生效/网签合同可登记实收");
        if (form == null || form.getAmount() == null || form.getAmount().signum() <= 0)
            throw new BizException("请填写实收金额");
        // 目标应收：指定 planId，否则取最早 DUE/OVERDUE 应收
        SalePaymentPlan plan = null;
        if (form.getPlanId() != null) {
            plan = planMapper.selectById(form.getPlanId());
            if (plan == null || !contractId.equals(plan.getContractId())) throw new BizException("应收计划不存在或不属于该合同");
        } else {
            plan = planMapper.selectOne(new LambdaQueryWrapper<SalePaymentPlan>()
                    .eq(SalePaymentPlan::getContractId, contractId)
                    .in(SalePaymentPlan::getStatus, "DUE", "OVERDUE")
                    .orderByAsc(SalePaymentPlan::getDueDate).orderByAsc(SalePaymentPlan::getSeq)
                    .last("LIMIT 1"));
        }
        if (plan == null) throw new BizException("该合同无可核销的应收计划");
        BigDecimal remaining = plan.getPlanAmount().subtract(SaleSupport.nvl(plan.getReceivedAmount()));
        if (form.getAmount().compareTo(remaining) > 0)
            throw new BizException("实收金额超出该期应收余额（余额 " + remaining + "）");

        SaleReceipt r = new SaleReceipt();
        r.setContractId(contractId);
        r.setPlanId(plan.getId());
        r.setReceivedAmount(form.getAmount());
        r.setPayWay(form.getMethod());
        r.setTicketNo(form.getTicketNo());
        r.setReceivedAt(form.getReceivedDate() == null ? LocalDateTime.now() : form.getReceivedDate());
        r.setReceiptNo(codeRuleService.next("SH", support.codeCtx(c.getProjectId())));
        receiptMapper.insert(r);

        // 核销：回写已收金额，满额置 RECEIVED
        plan.setReceivedAmount(SaleSupport.nvl(plan.getReceivedAmount()).add(form.getAmount()));
        if (plan.getReceivedAmount().compareTo(plan.getPlanAmount()) >= 0) plan.setStatus("RECEIVED");
        if (planMapper.updateById(plan) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return r.getId();
    }

    /**
     * 来账核销（银企直联/短信）：将一笔来账金额按到期先后分配到多个应收计划，
     * 超出应收余额部分记为"溢收/预收"（plan_id 为空）。返回生成的首笔实收单ID。
     */
    @Transactional
    public Long receiveSpan(Long contractId, BigDecimal amount, String method, String ticketNo, LocalDateTime at) {
        SaleContract c = requireContract(contractId);
        if (!"ACTIVE".equals(c.getStatus()) && !"FILED".equals(c.getStatus()))
            throw new BizException("仅生效/网签合同可核销来账");
        if (amount == null || amount.signum() <= 0) throw new BizException("来账金额无效");
        List<SalePaymentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId)
                .in(SalePaymentPlan::getStatus, "DUE", "OVERDUE")
                .orderByAsc(SalePaymentPlan::getDueDate).orderByAsc(SalePaymentPlan::getSeq));
        if (plans.isEmpty()) throw new BizException("该合同无待核销的应收计划");
        Map<String, String> ctx = support.codeCtx(c.getProjectId());
        BigDecimal left = amount;
        Long firstReceiptId = null;
        for (SalePaymentPlan p : plans) {
            if (left.signum() <= 0) break;
            BigDecimal remaining = SaleSupport.nvl(p.getPlanAmount()).subtract(SaleSupport.nvl(p.getReceivedAmount()));
            if (remaining.signum() <= 0) continue;
            BigDecimal take = left.min(remaining);
            SaleReceipt r = buildReceipt(contractId, p.getId(), take, method, ticketNo, at, ctx);
            if (firstReceiptId == null) firstReceiptId = r.getId();
            p.setReceivedAmount(SaleSupport.nvl(p.getReceivedAmount()).add(take));
            if (p.getReceivedAmount().compareTo(p.getPlanAmount()) >= 0) p.setStatus("RECEIVED");
            planMapper.updateById(p);
            left = left.subtract(take);
        }
        // 超出应收余额 → 溢收/预收（无对应应收计划）
        if (left.signum() > 0) {
            SaleReceipt over = buildReceipt(contractId, null, left, method, ticketNo, at, ctx);
            if (firstReceiptId == null) firstReceiptId = over.getId();
            left = BigDecimal.ZERO;
        }
        if (firstReceiptId == null) throw new BizException("该合同应收已结清，无法核销来账");
        return firstReceiptId;
    }

    private SaleReceipt buildReceipt(Long contractId, Long planId, BigDecimal amount, String method,
                                     String ticketNo, LocalDateTime at, Map<String, String> ctx) {
        SaleReceipt r = new SaleReceipt();
        r.setContractId(contractId);
        r.setPlanId(planId);
        r.setReceivedAmount(amount);
        r.setPayWay(method == null || method.isBlank() ? "转账" : method);
        r.setTicketNo(ticketNo);
        r.setReceivedAt(at == null ? LocalDateTime.now() : at);
        r.setReceiptNo(codeRuleService.next("SH", ctx));
        receiptMapper.insert(r);
        return r;
    }

    // ==================== 按揭跟踪 ====================

    public PageResult<SaleMortgage> pageMortgages(long current, long size, Long contractId) {
        LambdaQueryWrapper<SaleMortgage> qw = new LambdaQueryWrapper<>();
        if (contractId != null) qw.eq(SaleMortgage::getContractId, contractId);
        qw.orderByDesc(SaleMortgage::getId);
        Page<SaleMortgage> p = mortgageMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(m -> m.setContractNo(contractNo(m.getContractId())));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Long createMortgage(SaleMortgage m) {
        if (m == null || m.getContractId() == null) throw new BizException("请选择合同");
        requireContract(m.getContractId());
        if (m.getMortgageAmount() == null) throw new BizException("请填写按揭金额");
        m.setId(null);
        if (m.getStage() == null || m.getStage().isBlank()) m.setStage("APPLY");
        mortgageMapper.insert(m);
        return m.getId();
    }

    @Transactional
    public void updateMortgage(SaleMortgage m) {
        if (m == null || m.getId() == null) throw new BizException("缺少按揭ID");
        SaleMortgage db = mortgageMapper.selectById(m.getId());
        if (db == null) throw new BizException("按揭记录不存在");
        // 按揭阶段只可前向推进：APPLY→APPROVE→MORTGAGE→DISBURSE
        if (m.getStage() != null && !m.getStage().isBlank()) {
            int cur = stageIdx(db.getStage());
            int next = stageIdx(m.getStage());
            if (next <= cur) throw new BizException("按揭阶段不可回退");
            if ("DISBURSE".equals(m.getStage()) && m.getDisbursedAt() == null) m.setDisbursedAt(LocalDate.now());
        }
        m.setContractId(db.getContractId());
        if (mortgageMapper.updateById(m) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    private int stageIdx(String stage) {
        return switch (stage == null ? "" : stage) {
            case "APPLY" -> 0;
            case "APPROVE" -> 1;
            case "MORTGAGE" -> 2;
            case "DISBURSE" -> 3;
            default -> -1;
        };
    }

    // ==================== 私有 ====================

    private SaleContract requireContract(Long contractId) {
        if (contractId == null) throw new BizException("缺少合同ID");
        SaleContract c = contractMapper.selectById(contractId);
        if (c == null) throw new BizException("合同不存在");
        return c;
    }

    private String contractNo(Long contractId) {
        SaleContract c = contractMapper.selectById(contractId);
        return c == null ? null : c.getContractNo();
    }

    private void fillPlan(SalePaymentPlan p) {
        p.setContractNo(contractNo(p.getContractId()));
    }

    private void fillReceipt(SaleReceipt r) {
        r.setContractNo(contractNo(r.getContractId()));
    }

    private record PlanDef(String desc, String ratio, LocalDate due) {}

    @Data
    public static class ReceiveForm {
        private Long planId;
        private BigDecimal amount;
        private String method;
        private String ticketNo;
        private LocalDateTime receivedDate;
    }
}
