package com.reip.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.budget.entity.BudBudget;
import com.reip.budget.entity.BudBudgetMonth;
import com.reip.budget.mapper.BudMappers;
import com.reip.common.exception.BizException;
import com.reip.framework.param.ParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 全面预算管控服务（被成本/费用模块调用）
 *   B3 月度控制成本资金计划：成本付款申请按合同科目分摊校验当月成本预算余额
 *      （刚性=阻断 / 预警=放行留痕 / 不限=跳过；无预算行=不控）
 *   B4 月度控制费用资金计划：费用预算(部门×科目×月)合计 ≤ 同期月度费用预算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudControlService {

    private final BudMappers.BudgetMapper budgetMapper;
    private final BudMappers.MonthMapper monthMapper;
    private final BudMappers.ExpenseExecMapper expenseExecMapper;
    private final BudMappers.CostPlanMapper costPlanMapper;
    private final BudExecService execService;
    private final ParamService paramService;

    /**
     * B3 成本付款申请月度预算校验（提交审批时调用）
     * 管控月 = 申请提交当月；科目 = 合同分摊科目（无分摊回退合约规划科目，按金额占比分摊付款额）
     *
     * @return 预警信息（预警科目超预算时放行但留痕）；刚性科目超预算直接抛异常
     */
    public List<String> checkCostPayment(Long projectId, Long contractId, BigDecimal amount) {
        if (!paramService.getBool("bud.cost.strict", true)) return List.of();
        if (projectId == null || contractId == null || amount == null || amount.signum() <= 0) return List.of();

        // 科目及占比：合同分摊 → 回退合约规划科目（占比1）
        List<Map<String, Object>> allocs = costPlanMapper.selectContractAlloc(contractId);
        if (allocs.isEmpty()) {
            Map<String, Object> pa = costPlanMapper.selectContractPlanAccount(contractId);
            if (pa == null || pa.get("accountId") == null) return List.of(); // 无科目维度，不控
            Map<String, Object> one = new LinkedHashMap<>(pa);
            one.put("ratio", BigDecimal.ONE);
            allocs = List.of(one);
        }
        BigDecimal totalAlloc = allocs.stream().map(r -> toDecimal(r.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        List<String> warnings = new ArrayList<>();
        for (Map<String, Object> al : allocs) {
            Long accountId = al.get("accountId") == null ? null : ((Number) al.get("accountId")).longValue();
            if (accountId == null) continue;
            BigDecimal pay = totalAlloc.signum() > 0
                    ? amount.multiply(toDecimal(al.get("amount"))).divide(totalAlloc, 2, RoundingMode.HALF_UP)
                    : amount.divide(BigDecimal.valueOf(allocs.size()), 2, RoundingMode.HALF_UP);

            BudBudget b = findCostBudget(year, projectId, accountId);
            if (b == null) continue; // 未编成本预算 = 不控
            BudBudgetMonth m = monthMapper.selectOne(new LambdaQueryWrapper<BudBudgetMonth>()
                    .eq(BudBudgetMonth::getBudgetId, b.getId()).eq(BudBudgetMonth::getMonth, month));
            if (m == null) continue; // 该月未分解 = 不控

            BigDecimal actual = execService.monthActual(b, month);
            BigDecimal balance = BudSupport.nvl(m.getMonthAmount()).subtract(actual);
            if (balance.compareTo(pay) < 0) {
                String name = al.get("accountName") != null ? String.valueOf(al.get("accountName")) : b.getAccountName();
                if ("刚性".equals(b.getControlMode())) {
                    throw new BizException("月度成本预算超支（B3）：" + month + "月科目【" + name + "】余额 "
                            + balance + " 不足以覆盖付款 " + pay + "，请先调整月度预算");
                }
                if ("预警".equals(b.getControlMode())) {
                    warnings.add(month + "月科目【" + name + "】预算余额 " + balance + " 低于付款 " + pay + "（预警放行）");
                }
            }
        }
        if (!warnings.isEmpty()) log.warn("[全面预算B3] 成本付款预警 projectId={} contractId={} amount={} warnings={}",
                projectId, contractId, amount, warnings);
        return warnings;
    }

    /**
     * B4 费用预算编制/调整校验（ExpBudgetService 调用）
     * 费用预算(部门×科目×月) 合计 ≤ 同期月度费用预算；month 为空（全年池）对照年度预算额
     */
    public void checkExpenseBudget(Long deptId, Long accountId, Integer year, Integer month,
                                   BigDecimal amount, Long excludeId) {
        if (!paramService.getBool("bud.expense.strict", true)) return;
        if (deptId == null || accountId == null || year == null || amount == null || amount.signum() <= 0) return;

        BudBudget b = budgetMapper.selectOne(new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year)
                .eq(BudBudget::getBudgetType, "EXPENSE")
                .eq(BudBudget::getDeptId, deptId)
                .eq(BudBudget::getAccountId, accountId)
                .eq(BudBudget::getStatus, "APPROVED")
                .last("LIMIT 1"));
        if (b == null) return; // 未编全面预算 = 不控（兼容存量）

        BigDecimal cap;
        String scope;
        if (month != null) {
            BudBudgetMonth m = monthMapper.selectOne(new LambdaQueryWrapper<BudBudgetMonth>()
                    .eq(BudBudgetMonth::getBudgetId, b.getId()).eq(BudBudgetMonth::getMonth, month));
            if (m == null) return; // 该月未分解 = 不控
            cap = BudSupport.nvl(m.getMonthAmount());
            scope = month + "月";
        } else {
            cap = BudSupport.nvl(b.getAnnualAmount());
            scope = "年度";
        }

        BigDecimal current = month != null
                ? expenseExecMapper.sumExpBudgetMonth(year, deptId, accountId, month, excludeId)
                : expenseExecMapper.sumExpBudgetAnnual(year, deptId, accountId, excludeId);
        if (current.add(amount).compareTo(cap) > 0) {
            if ("刚性".equals(b.getControlMode())) {
                throw new BizException("费用预算超月度全面预算（B4）：" + scope + "科目【" + b.getAccountName()
                        + "】预算 " + cap + "，已编 " + current + "，本次 " + amount + "，超出 "
                        + current.add(amount).subtract(cap));
            }
            if ("预警".equals(b.getControlMode())) {
                log.warn("[全面预算B4] 费用预算预警 deptId={} accountId={} {}={} 超月度预算 {}",
                        deptId, accountId, scope, current.add(amount), cap);
            }
        }
    }

    // ==================== 私有 ====================

    private BudBudget findCostBudget(int year, Long projectId, Long accountId) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year)
                .eq(BudBudget::getBudgetType, "COST")
                .eq(BudBudget::getProjectId, projectId)
                .eq(BudBudget::getAccountId, accountId)
                .eq(BudBudget::getStatus, "APPROVED")
                .last("LIMIT 1"));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
    }
}
