package com.reip.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.fund.entity.FundFinance;
import com.reip.fund.entity.FundPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface FundMappers {

    @Mapper
    interface PlanMapper extends BaseMapper<FundPlan> {

        /** 按月查询资金计划（带项目名称） */
        @Select("""
            SELECT p.*, pr.name AS project_name
            FROM fund_plan p LEFT JOIN mdm_project pr ON pr.id = p.project_id
            WHERE p.plan_month = #{month}
            ORDER BY p.project_id
            """)
        List<FundPlan> selectByMonth(String month);
    }

    /** 付款申请查询（成本域表，跨模块只读） */
    @Mapper
    interface PaymentMapper {

        /** 大额付款清单（单笔 ≥ 阈值） */
        @Select("""
            SELECT id, payment_code AS paymentCode, project_id AS projectId, contract_id AS contractId,
                   apply_amount AS applyAmount, COALESCE(approved_amount, apply_amount) AS approvedAmount,
                   status, paid_date AS paidDate
            FROM cost_payment_apply
            WHERE deleted = 0 AND status IN ('APPROVED', 'PAID') AND apply_amount >= #{threshold}
            ORDER BY apply_amount DESC
            """)
        List<Map<String, Object>> selectLargePayments(BigDecimal threshold);

        /** 当日实付清单（含大额标记） */
        @Select("""
            SELECT id, payment_code AS paymentCode, project_id AS projectId, contract_id AS contractId,
                   apply_amount AS applyAmount, paid_amount AS paidAmount, paid_date AS paidDate,
                   CASE WHEN apply_amount >= #{threshold} THEN 1 ELSE 0 END AS isLarge
            FROM cost_payment_apply
            WHERE deleted = 0 AND status = 'PAID' AND paid_date = #{date}
            ORDER BY paid_amount DESC
            """)
        List<Map<String, Object>> selectDailyPaid(String date, BigDecimal threshold);

        /** 累计实付合计（Σ PAID paid_amount） */
        @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM cost_payment_apply WHERE deleted = 0 AND status = 'PAID'")
        BigDecimal sumPaidAmount();
    }

    /** 成本线月度资金计划：合同付款计划（当月未付部分，按项目） */
    @Mapper
    interface CostPlanFundMapper {

        @Select("""
            SELECT c.project_id AS projectId, SUM(pp.plan_amount - COALESCE(pp.paid_amount, 0)) AS amount
            FROM cost_payment_plan pp
            JOIN cost_contract c ON c.id = pp.contract_id AND c.deleted = 0
            WHERE pp.deleted = 0 AND pp.plan_date IS NOT NULL
              AND DATE_FORMAT(pp.plan_date, '%Y-%m') = #{month}
              AND (pp.plan_amount - COALESCE(pp.paid_amount, 0)) > 0
            GROUP BY c.project_id
            """)
        List<Map<String, Object>> selectMonthCostPlan(String month);

        /** 费用线月度资金计划：当月生效的费用预算合计（部门×科目，费用资金计划在费用科目上管控） */
        @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM exp_budget
            WHERE deleted = 0 AND status = 'ACTIVE' AND budget_year = #{year} AND month = #{month}
            """)
        BigDecimal sumMonthExpense(@Param("year") int year, @Param("month") int month);

        /** B5 对照：当月月度成本预算（项目×当月，全面预算成本线） */
        @Select("""
            SELECT b.project_id AS projectId, SUM(m.month_amount) AS budget
            FROM bud_budget b
            JOIN bud_budget_month m ON m.budget_id = b.id AND m.deleted = 0 AND m.month = #{month}
            WHERE b.deleted = 0 AND b.status = 'APPROVED' AND b.budget_type = 'COST' AND b.budget_year = #{year}
            GROUP BY b.project_id
            """)
        List<Map<String, Object>> selectMonthCostBudget(@Param("year") int year, @Param("month") int month);

        /** B5 对照：当月月度费用预算合计（全面预算费用线） */
        @Select("""
            SELECT COALESCE(SUM(m.month_amount), 0)
            FROM bud_budget b
            JOIN bud_budget_month m ON m.budget_id = b.id AND m.deleted = 0 AND m.month = #{month}
            WHERE b.deleted = 0 AND b.status = 'APPROVED' AND b.budget_type = 'EXPENSE' AND b.budget_year = #{year}
            """)
        BigDecimal sumMonthExpenseBudget(@Param("year") int year, @Param("month") int month);
    }

    /** 项目主数据查询（MDM 域表，跨模块只读） */
    @Mapper
    interface ProjectMapper {

        @Select("SELECT id FROM mdm_project WHERE deleted = 0 AND status = 'CONSTRUCTION'")
        List<Long> selectConstructionIds();
    }

    @Mapper
    interface FinanceMapper extends BaseMapper<FundFinance> {
    }
}
