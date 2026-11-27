package com.reip.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.fund.entity.FundPlan;
import com.reip.fund.mapper.FundMappers;
import com.reip.framework.param.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资金计划 / 收付执行 / 资金日报
 */
@Service
@RequiredArgsConstructor
public class FundPlanService {

    /** 演示值：每个在建项目月度销售回款（元） */
    private static final BigDecimal DEMO_MONTH_RECEIPT = new BigDecimal("30000000");
    /** 大额付款阈值默认值（sys_param: fund.large.pay.threshold 可覆盖） */
    private static final BigDecimal DEFAULT_LARGE_THRESHOLD = new BigDecimal("1000000");

    private final FundMappers.PlanMapper planMapper;
    private final FundMappers.PaymentMapper paymentMapper;
    private final FundMappers.ProjectMapper projectMapper;
    private final FundMappers.CostPlanFundMapper costPlanFundMapper;
    private final ParamService paramService;

    /** 该月各项目收付计划 + 汇总 + 大额付款标记 */
    public Map<String, Object> monthPlan(String month) {
        List<FundPlan> plans = planMapper.selectByMonth(month);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("receiptPlan", sum(plans, FundPlan::getReceiptPlan));
        summary.put("paymentPlan", sum(plans, FundPlan::getPaymentPlan));
        summary.put("netPlan", sum(plans, FundPlan::getNetPlan));
        summary.put("receiptActual", sum(plans, FundPlan::getReceiptActual));
        summary.put("paymentActual", sum(plans, FundPlan::getPaymentActual));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("plans", plans);
        result.put("summary", summary);
        result.put("largePayments", paymentMapper.selectLargePayments(largeThreshold()));
        return result;
    }

    /**
     * 自动汇总生成月度资金计划（幂等：覆盖 DRAFT，不动 FINAL）
     * 成本线 = 合同付款计划当月未付部分（按项目）；费用线 = 当月费用预算(部门×科目) → 集团汇总行
     * B5 一致性：付款计划 vs 当月月度预算（全面预算），超出部分标记资金缺口并写入明细
     * 收款 = 演示销售回款（在建项目每项目3000万）
     */
    @Transactional
    public Map<String, Object> generate(String month) {
        if (month == null || month.isBlank() || month.length() != 7) throw new BizException("请指定计划月份(yyyy-MM)");
        int year = Integer.parseInt(month.substring(0, 4));
        int monthNo = Integer.parseInt(month.substring(5, 7));

        // 成本线：合同付款计划当月未付
        Map<Long, BigDecimal> costByProject = costPlanFundMapper.selectMonthCostPlan(month).stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("projectId")).longValue(),
                        r -> toDecimal(r.get("amount"))));
        // 费用线：当月费用预算（集团口径）
        BigDecimal expenseTotal = costPlanFundMapper.sumMonthExpense(year, monthNo);
        // B5 对照口径：当月月度预算
        Map<Long, BigDecimal> costBudget = costPlanFundMapper.selectMonthCostBudget(year, monthNo).stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("projectId")).longValue(),
                        r -> toDecimal(r.get("budget"))));
        BigDecimal expenseBudget = costPlanFundMapper.sumMonthExpenseBudget(year, monthNo);

        Set<Long> projectIds = new LinkedHashSet<>(projectMapper.selectConstructionIds());
        projectIds.addAll(costByProject.keySet());

        List<FundPlan> exists = planMapper.selectList(new LambdaQueryWrapper<FundPlan>()
                .eq(FundPlan::getPlanMonth, month));
        Map<Long, List<FundPlan>> existsByProject = exists.stream()
                .filter(p -> p.getProjectId() != null)
                .collect(Collectors.groupingBy(FundPlan::getProjectId));
        FundPlan existsGroup = exists.stream().filter(p -> p.getProjectId() == null).findFirst().orElse(null);

        int created = 0, updated = 0, skipped = 0;
        for (Long projectId : projectIds) {
            List<FundPlan> same = existsByProject.getOrDefault(projectId, List.of());
            boolean hasFinal = same.stream().anyMatch(p -> "FINAL".equals(p.getVersionType()));
            if (hasFinal) { // 定稿不覆盖
                skipped++;
                continue;
            }
            BigDecimal payCost = costByProject.getOrDefault(projectId, BigDecimal.ZERO);
            BigDecimal receipt = DEMO_MONTH_RECEIPT;
            BigDecimal net = receipt.subtract(payCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gap = net.compareTo(BigDecimal.ZERO) < 0 ? net.negate() : BigDecimal.ZERO;
            // B5：成本付款计划 vs 当月月度成本预算（无预算=不控，不计缺口）
            BigDecimal bud = costBudget.get(projectId);
            BigDecimal over = bud != null ? payCost.subtract(bud).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            gap = gap.add(over);
            String detail = "{\"receipt\":\"演示销售回款\",\"costSource\":\"合同付款计划\","
                    + "\"costBudget\":" + (bud == null ? "null" : bud.toPlainString())
                    + ",\"costOver\":" + over.toPlainString() + "}";

            FundPlan draft = same.stream()
                    .filter(p -> "DRAFT".equals(p.getVersionType()))
                    .findFirst().orElse(null);
            if (draft == null) {
                draft = new FundPlan();
                draft.setPlanMonth(month);
                draft.setProjectId(projectId);
                draft.setVersionType("DRAFT");
                draft.setCreatedAt(LocalDateTime.now());
                draft.setReceiptActual(BigDecimal.ZERO);
                draft.setPaymentActual(BigDecimal.ZERO);
                draft.setReceiptPlan(receipt);
                draft.setPaymentPlan(payCost);
                draft.setNetPlan(net);
                draft.setGapAmount(gap);
                draft.setDetailJson(detail);
                planMapper.insert(draft);
                created++;
            } else {
                draft.setReceiptPlan(receipt);
                draft.setPaymentPlan(payCost);
                draft.setNetPlan(net);
                draft.setGapAmount(gap);
                draft.setDetailJson(detail);
                planMapper.updateById(draft);
                updated++;
            }
        }

        // 费用线：集团汇总行（项目ID=NULL），当月费用预算为付款来源
        if (expenseTotal.signum() > 0) {
            if (existsGroup != null && "FINAL".equals(existsGroup.getVersionType())) {
                skipped++;
            } else {
                FundPlan group = existsGroup;
                boolean isNew = group == null;
                if (isNew) {
                    group = new FundPlan();
                    group.setPlanMonth(month);
                    group.setProjectId(null);
                    group.setVersionType("DRAFT");
                    group.setCreatedAt(LocalDateTime.now());
                    group.setReceiptActual(BigDecimal.ZERO);
                    group.setPaymentActual(BigDecimal.ZERO);
                }
                group.setReceiptPlan(BigDecimal.ZERO);
                group.setPaymentPlan(expenseTotal);
                group.setNetPlan(expenseTotal.negate());
                group.setGapAmount(expenseTotal.subtract(expenseBudget).max(BigDecimal.ZERO));
                group.setDetailJson("{\"scope\":\"集团(费用线)\",\"expenseSource\":\"费用预算(部门×科目×月)\","
                        + "\"expenseBudget\":" + expenseBudget.toPlainString() + "}");
                if (isNew) {
                    planMapper.insert(group);
                    created++;
                } else {
                    planMapper.updateById(group);
                    updated++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("created", created);
        result.put("updated", updated);
        result.put("skippedFinal", skipped);
        result.put("expenseTotal", expenseTotal);
        return result;
    }

    /** 计划定稿 */
    @Transactional
    public void finalizePlan(Long id) {
        FundPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BizException("资金计划不存在");
        if ("FINAL".equals(plan.getVersionType())) throw new BizException("计划已是定稿状态");
        plan.setVersionType("FINAL");
        planMapper.updateById(plan);
    }

    /** 计划 vs 实际对比（每项目取 FINAL 优先，否则 DRAFT） */
    public Map<String, Object> execute(String month) {
        List<FundPlan> plans = planMapper.selectByMonth(month);
        Map<Long, FundPlan> selected = new LinkedHashMap<>();
        for (FundPlan p : plans) { // 按 project_id 排序返回，FINAL 覆盖 DRAFT
            if (p.getProjectId() == null) continue;
            FundPlan cur = selected.get(p.getProjectId());
            if (cur == null || ("FINAL".equals(p.getVersionType()) && !"FINAL".equals(cur.getVersionType()))) {
                selected.put(p.getProjectId(), p);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FundPlan p : selected.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", p.getProjectId());
            row.put("projectName", p.getProjectName());
            row.put("versionType", p.getVersionType());
            row.put("receiptPlan", p.getReceiptPlan());
            row.put("paymentPlan", p.getPaymentPlan());
            row.put("receiptActual", p.getReceiptActual());
            row.put("paymentActual", p.getPaymentActual());
            row.put("receiptRate", rate(p.getReceiptActual(), p.getReceiptPlan()));
            row.put("paymentRate", rate(p.getPaymentActual(), p.getPaymentPlan()));
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        List<FundPlan> all = new ArrayList<>(selected.values());
        summary.put("receiptPlan", sum(all, FundPlan::getReceiptPlan));
        summary.put("paymentPlan", sum(all, FundPlan::getPaymentPlan));
        summary.put("receiptActual", sum(all, FundPlan::getReceiptActual));
        summary.put("paymentActual", sum(all, FundPlan::getPaymentActual));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("rows", rows);
        result.put("summary", summary);
        return result;
    }

    /** 资金日报：当日实付清单（大额标记）+ 汇总 */
    public Map<String, Object> daily(String date) {
        List<Map<String, Object>> payments = paymentMapper.selectDailyPaid(date, largeThreshold());

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal largeAmount = BigDecimal.ZERO;
        int largeCount = 0;
        for (Map<String, Object> p : payments) {
            BigDecimal paid = toDecimal(p.get("paidAmount"));
            totalAmount = totalAmount.add(paid);
            if (((Number) p.getOrDefault("isLarge", 0)).intValue() == 1) {
                largeAmount = largeAmount.add(paid);
                largeCount++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", payments.size());
        summary.put("totalAmount", totalAmount);
        summary.put("largeCount", largeCount);
        summary.put("largeAmount", largeAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("payments", payments);
        result.put("summary", summary);
        return result;
    }

    /** 大额付款阈值（sys_param: fund.large.pay.threshold，缺省100万） */
    private BigDecimal largeThreshold() {
        return paramService.getDecimal("fund.large.pay.threshold", DEFAULT_LARGE_THRESHOLD);
    }

    private BigDecimal rate(BigDecimal actual, BigDecimal plan) {
        if (actual == null || plan == null || plan.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actual.divide(plan, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<FundPlan> plans, java.util.function.Function<FundPlan, BigDecimal> getter) {
        return plans.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toDecimal(Object v) {
        return v == null ? BigDecimal.ZERO : (v instanceof BigDecimal b ? b : new BigDecimal(v.toString()));
    }
}
