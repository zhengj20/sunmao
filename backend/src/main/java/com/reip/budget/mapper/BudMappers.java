package com.reip.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.budget.entity.BudAdjust;
import com.reip.budget.entity.BudBudget;
import com.reip.budget.entity.BudBudgetMonth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 全面预算 Mapper 聚合（模式同 ExpenseMappers / CostMappers）
 * 含年度/月度预算基础 CRUD、跨模块只读执行口径查询
 */
public interface BudMappers {

    @Mapper
    interface BudgetMapper extends BaseMapper<BudBudget> {
    }

    /** 成本执行口径：已审批/已付款的成本付款，按合同科目分摊到（项目×科目×月） */
    @Mapper
    interface CostExecMapper {

        /**
         * 成本预算执行额：Σ 已审付款按合同科目分摊
         * 科目来源：cost_contract_alloc（按分摊金额占比）→ 无分摊时回退合约规划明细科目
         * 月份口径：PAID 取实付月，APPROVED 取申请创建月
         */
        @Select("""
            SELECT c.project_id AS projectId,
                   COALESCE(al.account_id, pi.account_id) AS accountId,
                   DATE_FORMAT(DATE(COALESCE(pa.paid_date, pa.created_at)), '%Y-%m') AS ym,
                   SUM(COALESCE(pa.approved_amount, pa.apply_amount) * COALESCE(al.amount / at.total, 1)) AS amount
            FROM cost_payment_apply pa
            JOIN cost_contract c ON c.id = pa.contract_id AND c.deleted = 0
            LEFT JOIN cost_contract_alloc al ON al.contract_id = c.id AND al.deleted = 0
            LEFT JOIN (SELECT contract_id, SUM(amount) AS total FROM cost_contract_alloc
                       WHERE deleted = 0 GROUP BY contract_id) at ON at.contract_id = c.id
            LEFT JOIN cost_plan_item pi ON pi.id = c.plan_item_id AND pi.deleted = 0
            WHERE pa.deleted = 0 AND pa.status IN ('APPROVED', 'PAID')
            GROUP BY c.project_id, COALESCE(al.account_id, pi.account_id),
                     DATE_FORMAT(DATE(COALESCE(pa.paid_date, pa.created_at)), '%Y-%m')
            """)
        List<Map<String, Object>> selectCostExecution();
    }

    /** 费用执行口径：费用预算台账（部门×科目×月，占用+核销） */
    @Mapper
    interface ExpenseExecMapper {

        /** 费用预算执行额：exp_budget 按（部门×科目×月），执行=占用+核销（month 为空视为全年） */
        @Select("""
            SELECT b.dept_id AS deptId, b.account_id AS accountId,
                   b.budget_year AS year, b.month AS month,
                   SUM(b.occupy_amount + b.writeoff_amount) AS amount
            FROM exp_budget b
            WHERE b.deleted = 0 AND b.status = 'ACTIVE'
            GROUP BY b.dept_id, b.account_id, b.budget_year, b.month
            """)
        List<Map<String, Object>> selectExpenseExecution();

        /** 费用线月度资金计划：当月生效的费用预算合计（费用资金计划在费用科目上管控） */
        @Select("""
            SELECT COALESCE(SUM(b.amount), 0) AS total,
                   COALESCE(SUM(CASE WHEN b.account_id IS NOT NULL THEN b.amount ELSE 0 END), 0) AS withAccount
            FROM exp_budget b
            WHERE b.deleted = 0 AND b.status = 'ACTIVE'
              AND b.budget_year = #{year} AND b.month = #{month}
            """)
        Map<String, Object> selectExpenseMonthFund(@Param("year") int year, @Param("month") int month);

        /** 费用线资金计划按月（当年生效费用预算，month 为空的年度池不计入月度） */
        @Select("""
            SELECT b.month AS m, SUM(b.amount) AS amount
            FROM exp_budget b
            WHERE b.deleted = 0 AND b.status = 'ACTIVE'
              AND b.budget_year = #{year} AND b.month IS NOT NULL
            GROUP BY b.month
            """)
        List<Map<String, Object>> selectExpenseFundPlanByMonth(@Param("year") int year);

        /** B4 校验用：同年度×部门×科目×月的费用预算合计（排除指定行） */
        @Select("""
            SELECT COALESCE(SUM(b.amount), 0) FROM exp_budget b
            WHERE b.deleted = 0 AND b.status = 'ACTIVE'
              AND b.budget_year = #{year} AND b.dept_id = #{deptId} AND b.account_id = #{accountId}
              AND b.month = #{month} AND (#{excludeId} IS NULL OR b.id <> #{excludeId})
            """)
        BigDecimal sumExpBudgetMonth(@Param("year") int year, @Param("deptId") Long deptId,
                                     @Param("accountId") Long accountId, @Param("month") int month,
                                     @Param("excludeId") Long excludeId);

        /** B4 校验用：同年度×部门×科目的年度费用预算合计（month 为空的全年池，排除指定行） */
        @Select("""
            SELECT COALESCE(SUM(b.amount), 0) FROM exp_budget b
            WHERE b.deleted = 0 AND b.status = 'ACTIVE'
              AND b.budget_year = #{year} AND b.dept_id = #{deptId} AND b.account_id = #{accountId}
              AND b.month IS NULL AND (#{excludeId} IS NULL OR b.id <> #{excludeId})
            """)
        BigDecimal sumExpBudgetAnnual(@Param("year") int year, @Param("deptId") Long deptId,
                                      @Param("accountId") Long accountId, @Param("excludeId") Long excludeId);
    }

    /** 成本线月度资金计划：合同付款计划（当月未付部分） */
    @Mapper
    interface CostPlanMapper {

        @Select("""
            SELECT c.project_id AS projectId,
                   SUM(pp.plan_amount - COALESCE(pp.paid_amount, 0)) AS amount
            FROM cost_payment_plan pp
            JOIN cost_contract c ON c.id = pp.contract_id AND c.deleted = 0
            WHERE pp.deleted = 0 AND pp.plan_date IS NOT NULL
              AND DATE_FORMAT(pp.plan_date, '%Y-%m') = #{month}
              AND (pp.plan_amount - COALESCE(pp.paid_amount, 0)) > 0
            GROUP BY c.project_id
            """)
        List<Map<String, Object>> selectCostMonthFund(String month);

        /** 成本线资金计划按月（当年 12 个月，合同付款计划未付部分） */
        @Select("""
            SELECT DATE_FORMAT(pp.plan_date, '%m') AS m,
                   SUM(pp.plan_amount - COALESCE(pp.paid_amount, 0)) AS amount
            FROM cost_payment_plan pp
            WHERE pp.deleted = 0 AND pp.plan_date IS NOT NULL
              AND DATE_FORMAT(pp.plan_date, '%Y') = #{year}
              AND (pp.plan_amount - COALESCE(pp.paid_amount, 0)) > 0
            GROUP BY DATE_FORMAT(pp.plan_date, '%m')
            """)
        List<Map<String, Object>> selectCostFundPlanByMonth(@Param("year") String year);

        /** 合同科目占比（分摊金额 / 合同分摊总额）；无分摊时返回空 */
        @Select("""
            SELECT al.account_id AS accountId, al.account_name AS accountName, al.amount AS amount
            FROM cost_contract_alloc al
            WHERE al.contract_id = #{contractId} AND al.deleted = 0
            """)
        List<Map<String, Object>> selectContractAlloc(Long contractId);

        /** 合同对应合约规划明细科目（无分摊时的回退口径） */
        @Select("""
            SELECT pi.account_id AS accountId, pi.account_name AS accountName
            FROM cost_contract c
            LEFT JOIN cost_plan_item pi ON pi.id = c.plan_item_id AND pi.deleted = 0
            WHERE c.id = #{contractId} AND c.deleted = 0
            """)
        Map<String, Object> selectContractPlanAccount(Long contractId);
    }

    @Mapper
    interface MonthMapper extends BaseMapper<BudBudgetMonth> {
    }

    /** 预算调整单 */
    @Mapper
    interface AdjustMapper extends BaseMapper<BudAdjust> {
    }
}
