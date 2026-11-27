package com.reip.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.budget.entity.BudBudget;
import com.reip.budget.entity.BudBudgetMonth;
import com.reip.budget.mapper.BudMappers;
import com.reip.common.exception.BizException;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 全面预算执行口径 + 执行分析（年度/月度执行率、管控链）
 * 执行口径（只读计算，不落库）：
 *   成本执行 = Σ 已审/已付成本付款（按合同科目分摊，PAID取实付月、APPROVED取申请月）
 *   费用执行 = Σ exp_budget（占用+核销，部门×科目×月）
 */
@Service
@RequiredArgsConstructor
public class BudExecService {

    private final BudMappers.BudgetMapper budgetMapper;
    private final BudMappers.MonthMapper monthMapper;
    private final BudMappers.CostExecMapper costExecMapper;
    private final BudMappers.ExpenseExecMapper expenseExecMapper;
    private final BudMappers.CostPlanMapper costPlanMapper;
    private final MdmMappers.OrgMapper orgMapper;
    private final MdmMappers.ProjectMapper projectMapper;

    // ==================== 执行额 ====================

    /** 年度执行额 */
    public BigDecimal annualActual(BudBudget b) {
        if ("COST".equals(b.getBudgetType())) {
            return costExecMapper.selectCostExecution().stream()
                    .filter(r -> eq(r.get("projectId"), b.getProjectId()) && eq(r.get("accountId"), b.getAccountId()))
                    .filter(r -> ymOf(r.get("ym")).startsWith(String.valueOf(b.getBudgetYear())))
                    .map(r -> dec(r.get("amount")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return expenseExecMapper.selectExpenseExecution().stream()
                .filter(r -> eq(r.get("deptId"), b.getDeptId()) && eq(r.get("accountId"), b.getAccountId())
                        && eq(r.get("year"), b.getBudgetYear()))
                .map(r -> dec(r.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 某月执行额 */
    public BigDecimal monthActual(BudBudget b, int month) {
        String ym = b.getBudgetYear() + "-" + "%02d".formatted(month);
        if ("COST".equals(b.getBudgetType())) {
            return costExecMapper.selectCostExecution().stream()
                    .filter(r -> eq(r.get("projectId"), b.getProjectId()) && eq(r.get("accountId"), b.getAccountId())
                            && Objects.equals(String.valueOf(r.get("ym")), ym))
                    .map(r -> dec(r.get("amount")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return expenseExecMapper.selectExpenseExecution().stream()
                .filter(r -> eq(r.get("deptId"), b.getDeptId()) && eq(r.get("accountId"), b.getAccountId())
                        && eq(r.get("year"), b.getBudgetYear()) && eq(r.get("month"), month))
                .map(r -> dec(r.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 一次加载，填充月度分解的执行额/余额/超预算标记（未分解空行：余额留空，有执行即超预算） */
    public void fillMonths(BudBudget b, List<BudBudgetMonth> list) {
        if (list.isEmpty()) return;
        Map<String, BigDecimal> exec = "COST".equals(b.getBudgetType())
                ? costExecMapper.selectCostExecution().stream().collect(java.util.stream.Collectors.toMap(
                        r -> key3(r.get("projectId"), r.get("accountId"), r.get("ym")),
                        r -> dec(r.get("amount")), BigDecimal::add))
                : expenseExecMapper.selectExpenseExecution().stream().collect(java.util.stream.Collectors.toMap(
                        r -> keyExp(r.get("deptId"), r.get("accountId"), r.get("year"), r.get("month")),
                        r -> dec(r.get("amount")), BigDecimal::add));
        for (BudBudgetMonth m : list) {
            String ym = b.getBudgetYear() + "-" + "%02d".formatted(m.getMonth());
            BigDecimal actual = "COST".equals(b.getBudgetType())
                    ? exec.getOrDefault(key3(b.getProjectId(), b.getAccountId(), ym), BigDecimal.ZERO)
                    : exec.getOrDefault(keyExp(b.getDeptId(), b.getAccountId(), b.getBudgetYear(), m.getMonth()), BigDecimal.ZERO);
            m.setActualAmount(actual);
            if (m.getMonthAmount() != null) {
                m.setBalance(m.getMonthAmount().subtract(actual));
                m.setOverBudget(actual.compareTo(m.getMonthAmount()) > 0);
            } else {
                m.setBalance(null);
                m.setOverBudget(actual.signum() > 0);
            }
        }
    }

    /** 批量填充年度预算的执行额/执行率（执行聚合一次加载，避免逐行重查询） */
    public void fillAnnualActuals(List<BudBudget> list) {
        if (list == null || list.isEmpty()) return;
        Map<String, BigDecimal> cost = new HashMap<>();
        for (Map<String, Object> r : costExecMapper.selectCostExecution()) {
            Object ym = r.get("ym");
            if (ym == null) continue;
            String s = String.valueOf(ym);
            if (s.length() < 4) continue;
            cost.merge(r.get("projectId") + "|" + r.get("accountId") + "|" + s.substring(0, 4),
                    dec(r.get("amount")), BigDecimal::add);
        }
        Map<String, BigDecimal> expense = new HashMap<>();
        for (Map<String, Object> r : expenseExecMapper.selectExpenseExecution()) {
            expense.merge(r.get("deptId") + "|" + r.get("accountId") + "|" + r.get("year"),
                    dec(r.get("amount")), BigDecimal::add);
        }
        for (BudBudget b : list) {
            if (b.getActualAmount() != null) continue;
            boolean costLine = "COST".equals(b.getBudgetType());
            String k = costLine
                    ? b.getProjectId() + "|" + b.getAccountId() + "|" + b.getBudgetYear()
                    : b.getDeptId() + "|" + b.getAccountId() + "|" + b.getBudgetYear();
            BigDecimal actual = (costLine ? cost : expense).getOrDefault(k, BigDecimal.ZERO);
            b.setActualAmount(actual);
            b.setExecRate(rate(actual, b.getAnnualAmount()));
        }
    }

    // ==================== 执行分析 ====================

    /** 年度执行总览：成本线/费用线 预算-分解-执行-执行率 + 科目明细 */
    public Map<String, Object> overview(int year) {
        List<BudBudget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year)
                .in(BudBudget::getStatus, "DRAFT", "APPROVED"));
        Map<String, Object> cost = typeStat(budgets, "COST");
        Map<String, Object> expense = typeStat(budgets, "EXPENSE");

        List<Map<String, Object>> byAccount = new ArrayList<>();
        for (BudBudget b : budgets) {
            if (!"APPROVED".equals(b.getStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("budgetId", b.getId());
            row.put("budgetCode", b.getBudgetCode());
            row.put("type", b.getBudgetType());
            row.put("scopeName", b.getProjectId() != null ? nameOfProject(b.getProjectId()) : nameOfOrg(b.getDeptId()));
            row.put("accountName", b.getAccountName());
            row.put("controlMode", b.getControlMode());
            row.put("annual", BudSupport.nvl(b.getAnnualAmount()));
            row.put("allocated", BudSupport.nvl(b.getAllocatedAmount()));
            BigDecimal actual = annualActual(b);
            row.put("actual", actual);
            row.put("rate", rate(actual, b.getAnnualAmount()));
            byAccount.add(row);
        }
        byAccount.sort(Comparator.comparing((Map<String, Object> x) -> dec(x.get("actual")), Comparator.reverseOrder()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("cost", cost);
        result.put("expense", expense);
        result.put("total", mergeStat(cost, expense));
        result.put("byAccount", byAccount);
        return result;
    }

    /** 月度趋势：12 个月 预算 vs 执行（成本线/费用线） */
    public Map<String, Object> trend(int year) {
        Map<String, BigDecimal> monthBudget = new HashMap<>();
        List<BudBudget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year).eq(BudBudget::getStatus, "APPROVED"));
        List<Long> ids = budgets.stream().map(BudBudget::getId).toList();
        if (!ids.isEmpty()) {
            for (BudBudgetMonth m : monthMapper.selectList(new LambdaQueryWrapper<BudBudgetMonth>()
                    .in(BudBudgetMonth::getBudgetId, ids))) {
                BudBudget b = budgets.stream().filter(x -> x.getId().equals(m.getBudgetId())).findFirst().orElse(null);
                if (b == null) continue;
                String k = b.getBudgetType() + "|" + m.getMonth();
                monthBudget.merge(k, BudSupport.nvl(m.getMonthAmount()), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> costActual = costExecMapper.selectCostExecution().stream()
                .filter(r -> ymOf(r.get("ym")).startsWith(String.valueOf(year)))
                .collect(java.util.stream.Collectors.toMap(r -> "COST|" + r.get("ym").toString().substring(5),
                        r -> dec(r.get("amount")), BigDecimal::add));
        Map<String, BigDecimal> expActual = expenseExecMapper.selectExpenseExecution().stream()
                .filter(r -> eq(r.get("year"), year))
                .collect(java.util.stream.Collectors.toMap(
                        r -> "EXPENSE|" + (r.get("month") == null ? "A" : "%02d".formatted(((Number) r.get("month")).intValue())),
                        r -> dec(r.get("amount")), BigDecimal::add));

        List<Map<String, Object>> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String mm = "%02d".formatted(m);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m);
            row.put("costBudget", monthBudget.getOrDefault("COST|" + m, BigDecimal.ZERO));
            row.put("costActual", costActual.getOrDefault("COST|" + mm, BigDecimal.ZERO));
            row.put("expenseBudget", monthBudget.getOrDefault("EXPENSE|" + m, BigDecimal.ZERO));
            row.put("expenseActual", expActual.getOrDefault("EXPENSE|" + mm, BigDecimal.ZERO));
            months.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("months", months);
        return result;
    }

    /**
     * 月度资金计划执行：12 个月 资金计划 vs 实际
     *   成本资金计划 = 合同付款计划（计划月未付部分）；成本实际 = 已审/已付成本付款（PAID取实付月）
     *   费用资金计划 = 当月生效费用预算；费用实际 = 费用预算占用+核销
     */
    public Map<String, Object> fundMonths(int year) {
        Map<String, BigDecimal> costPlan = new HashMap<>();
        for (Map<String, Object> r : costPlanMapper.selectCostFundPlanByMonth(String.valueOf(year))) {
            Object m = r.get("m");
            if (m != null) costPlan.merge(m.toString(), dec(r.get("amount")), BigDecimal::add);
        }
        Map<String, BigDecimal> expPlan = new HashMap<>();
        for (Map<String, Object> r : expenseExecMapper.selectExpenseFundPlanByMonth(year)) {
            Object m = r.get("m");
            if (m != null) expPlan.merge(m.toString(), dec(r.get("amount")), BigDecimal::add);
        }
        Map<String, BigDecimal> costActual = costExecMapper.selectCostExecution().stream()
                .filter(r -> ymOf(r.get("ym")).startsWith(String.valueOf(year)))
                .collect(java.util.stream.Collectors.toMap(r -> ymOf(r.get("ym")).substring(5),
                        r -> dec(r.get("amount")), BigDecimal::add));
        Map<String, BigDecimal> expActual = expenseExecMapper.selectExpenseExecution().stream()
                .filter(r -> eq(r.get("year"), year) && r.get("month") != null)
                .collect(java.util.stream.Collectors.toMap(r -> String.valueOf(((Number) r.get("month")).intValue()),
                        r -> dec(r.get("amount")), BigDecimal::add));

        BigDecimal sumCostPlan = BigDecimal.ZERO, sumCostActual = BigDecimal.ZERO;
        BigDecimal sumExpPlan = BigDecimal.ZERO, sumExpActual = BigDecimal.ZERO;
        List<Map<String, Object>> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String mm = "%02d".formatted(m);
            BigDecimal cp = costPlan.getOrDefault(mm, BigDecimal.ZERO);
            BigDecimal ca = costActual.getOrDefault(mm, BigDecimal.ZERO);
            BigDecimal ep = expPlan.getOrDefault(String.valueOf(m), BigDecimal.ZERO);
            BigDecimal ea = expActual.getOrDefault(String.valueOf(m), BigDecimal.ZERO);
            sumCostPlan = sumCostPlan.add(cp);
            sumCostActual = sumCostActual.add(ca);
            sumExpPlan = sumExpPlan.add(ep);
            sumExpActual = sumExpActual.add(ea);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m);
            row.put("costPlan", cp);
            row.put("costActual", ca);
            row.put("costRate", rate(ca, cp));
            row.put("expensePlan", ep);
            row.put("expenseActual", ea);
            row.put("expenseRate", rate(ea, ep));
            months.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("months", months);
        result.put("total", Map.of("costPlan", sumCostPlan, "costActual", sumCostActual,
                "expensePlan", sumExpPlan, "expenseActual", sumExpActual));
        return result;
    }

    /**
     * 管控链（B5）：年度预算 → 月度预算 → 资金计划
     * 成本线资金计划 = 合同付款计划(当月未付)；费用线资金计划 = 当月费用预算(部门×科目)
     */
    public Map<String, Object> chain(int year, int month) {
        if (month < 1 || month > 12) throw new BizException("月份须为 1-12");
        String ym = year + "-" + "%02d".formatted(month);

        List<BudBudget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year).eq(BudBudget::getStatus, "APPROVED"));
        List<Long> ids = budgets.stream().map(BudBudget::getId).toList();
        Map<String, BigDecimal> monthBudget = new HashMap<>();
        if (!ids.isEmpty()) {
            for (BudBudgetMonth m : monthMapper.selectList(new LambdaQueryWrapper<BudBudgetMonth>()
                    .in(BudBudgetMonth::getBudgetId, ids).eq(BudBudgetMonth::getMonth, month))) {
                BudBudget b = budgets.stream().filter(x -> x.getId().equals(m.getBudgetId())).findFirst().orElse(null);
                if (b == null) continue;
                monthBudget.merge(b.getBudgetType(), BudSupport.nvl(m.getMonthAmount()), BigDecimal::add);
            }
        }
        BigDecimal annualCost = budgets.stream().filter(b -> "COST".equals(b.getBudgetType()))
                .map(BudBudget::getAnnualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualExp = budgets.stream().filter(b -> "EXPENSE".equals(b.getBudgetType()))
                .map(BudBudget::getAnnualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mCost = monthBudget.getOrDefault("COST", BigDecimal.ZERO);
        BigDecimal mExp = monthBudget.getOrDefault("EXPENSE", BigDecimal.ZERO);

        // 资金计划：成本线=合同付款计划当月未付；费用线=当月费用预算
        BigDecimal fundCost = costPlanMapper.selectCostMonthFund(ym).stream()
                .map(r -> dec(r.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> fundExp = expenseExecMapper.selectExpenseMonthFund(year, month);
        BigDecimal fundExpense = fundExp == null ? BigDecimal.ZERO : dec(fundExp.get("total"));

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("budget", mCost);
        cost.put("plan", fundCost);
        cost.put("gap", gapOf(fundCost, mCost));
        Map<String, Object> expense = new LinkedHashMap<>();
        expense.put("budget", mExp);
        expense.put("plan", fundExpense);
        expense.put("gap", gapOf(fundExpense, mExp));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("annual", Map.of("cost", annualCost, "expense", annualExp,
                "total", annualCost.add(annualExp)));
        result.put("monthly", Map.of("cost", mCost, "expense", mExp, "total", mCost.add(mExp)));
        result.put("fund", Map.of("cost", cost, "expense", expense,
                "total", fundCost.add(fundExpense)));
        result.put("over", gapOf(fundCost, mCost).signum() > 0 || gapOf(fundExpense, mExp).signum() > 0);
        return result;
    }

    // ==================== 私有 ====================

    private Map<String, Object> typeStat(List<BudBudget> budgets, String type) {
        List<BudBudget> list = budgets.stream()
                .filter(b -> type.equals(b.getBudgetType()) && "APPROVED".equals(b.getStatus()))
                .toList();
        BigDecimal annual = list.stream().map(BudBudget::getAnnualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = list.stream().map(BudBudget::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = list.stream().map(this::annualActual).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", list.size());
        m.put("annual", annual);
        m.put("allocated", allocated);
        m.put("unallocated", annual.subtract(allocated));
        m.put("actual", actual);
        m.put("rate", rate(actual, annual));
        return m;
    }

    private Map<String, Object> mergeStat(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annual", dec(a.get("annual")).add(dec(b.get("annual"))));
        m.put("allocated", dec(a.get("allocated")).add(dec(b.get("allocated"))));
        m.put("actual", dec(a.get("actual")).add(dec(b.get("actual"))));
        m.put("rate", rate(dec(a.get("actual")).add(dec(b.get("actual"))), dec(a.get("annual")).add(dec(b.get("annual")))));
        return m;
    }

    private String nameOfProject(Long id) {
        if (id == null) return "-";
        var p = projectMapper.selectById(id);
        return p == null ? "项目#" + id : p.getName();
    }

    private String nameOfOrg(Long id) {
        if (id == null) return "-";
        var o = orgMapper.selectById(id);
        return o == null ? "部门#" + id : o.getName();
    }

    private static boolean eq(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Number na && b instanceof Number nb) return na.longValue() == nb.longValue();
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static String ymOf(Object ym) {
        return ym == null ? "" : String.valueOf(ym);
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
    }

    private static BigDecimal rate(BigDecimal actual, BigDecimal base) {
        if (base == null || base.signum() == 0) return BigDecimal.ZERO;
        return actual.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal gapOf(BigDecimal plan, BigDecimal budget) {
        BigDecimal gap = plan.subtract(BudSupport.nvl(budget));
        return gap.signum() > 0 ? gap : BigDecimal.ZERO;
    }

    private static String key3(Object a, Object b, Object c) {
        return a + "|" + b + "|" + c;
    }

    private static String keyExp(Object a, Object b, Object c, Object d) {
        return a + "|" + b + "|" + c + "|" + d;
    }
}
