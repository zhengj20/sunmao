package com.reip.proc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.proc.entity.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 招采模块 Mapper 集合（含跨模块查询：mdm_supplier / cost_plan_item）
 */
public interface ProcMappers {

    @Mapper interface RequirementMapper extends BaseMapper<ProcRequirement> {}
    @Mapper interface PlanMapper extends BaseMapper<ProcPlan> {}
    @Mapper interface PlanItemMapper extends BaseMapper<ProcPlanItem> {}
    @Mapper interface ProjectMapper extends BaseMapper<ProcProject> {}
    @Mapper interface EntryMapper extends BaseMapper<ProcEntry> {}
    @Mapper interface TenderMapper extends BaseMapper<ProcTender> {}
    @Mapper interface BidMapper extends BaseMapper<ProcBid> {}
    @Mapper interface EvaluationMapper extends BaseMapper<ProcEvaluation> {}
    @Mapper interface EvalDetailMapper extends BaseMapper<ProcEvalDetail> {}
    @Mapper interface AwardMapper extends BaseMapper<ProcAward> {}
    @Mapper interface DirectMapper extends BaseMapper<ProcDirect> {}
    @Mapper interface FrameworkMapper extends BaseMapper<ProcFramework> {}
    @Mapper interface FrameworkOrderMapper extends BaseMapper<ProcFrameworkOrder> {}
    @Mapper interface CollusionWarnMapper extends BaseMapper<ProcCollusionWarn> {}
    @Mapper interface BidItemMapper extends BaseMapper<ProcBidItem> {}
    @Mapper interface MiscMapper extends BaseMapper<ProcMisc> {}
    @Mapper interface MiscCompareMapper extends BaseMapper<ProcMiscCompare> {}
    @Mapper interface ExpertMapper extends BaseMapper<ProcExpert> {}

    /** 跨模块：供应商主数据（黑名单校验/名称回填） */
    @Mapper
    interface MdmMapper {
        @Select("SELECT id, name, lib_type AS libType FROM mdm_supplier WHERE id = #{id} AND deleted = 0")
        Map<String, Object> selectSupplier(Long id);
    }

    /** 跨模块：合约规划明细（定标余量控制，只读） */
    @Mapper
    interface CostPlanItemMapper {

        @Select("""
            SELECT id, planned_amount AS plannedAmount, contracted_amount AS contractedAmount,
                   pending_amount AS pendingAmount
            FROM cost_plan_item WHERE id = #{id} AND deleted = 0
            """)
        Map<String, Object> selectPlanItem(Long id);
    }

    /** 全流程台账与统计（立项维度 JOIN 定标/发标） */
    @Mapper
    interface LedgerMapper {

        @Select("""
            <script>
            SELECT p.id                    AS procProjectId,
                   p.project_code           AS projectCode,
                   p.title                  AS title,
                   p.procure_method         AS procureMethod,
                   p.budget_amount          AS budgetAmount,
                   a.award_code             AS awardCode,
                   a.winner_supplier_name   AS winnerSupplierName,
                   a.award_amount           AS awardAmount,
                   (a.award_amount - p.budget_amount) AS deviation,
                   CASE WHEN p.budget_amount &gt; 0
                        THEN ROUND((a.award_amount - p.budget_amount) / p.budget_amount * 100, 2) END AS deviationPct,
                   DATEDIFF(a.created_at, t.publish_time) AS cycleDays,
                   p.status                 AS status
            FROM proc_project p
            LEFT JOIN proc_award a ON a.id = (SELECT MAX(a2.id) FROM proc_award a2
                                              WHERE a2.proc_project_id = p.id AND a2.deleted = 0)
            LEFT JOIN proc_tender t ON t.id = (SELECT MAX(t2.id) FROM proc_tender t2
                                              WHERE t2.proc_project_id = p.id AND t2.deleted = 0)
            WHERE p.deleted = 0
            <if test="projectId != null"> AND p.project_id = #{projectId} </if>
            <if test="kw != null and kw != ''"> AND p.title LIKE CONCAT('%', #{kw}, '%') </if>
            ORDER BY p.id DESC
            </script>
            """)
        List<Map<String, Object>> selectLedgerPage(Page<?> page, @Param("projectId") Long projectId,
                                                   @Param("kw") String kw);

        /**
         * 招采过程总览：以立项为主轴，一次性返回 9 个节点的完成标记（0/1）。
         * 需求←(plan_item→requirement)、计划←plan_item_id、立项←status、入围/发标/回标/评标←子表存在、
         * 定标←award(APPROVED)、合同←award.contract_generated/contract_id。
         */
        @Select("""
            <script>
            SELECT p.id AS procProjectId,
                   p.project_code AS projectCode,
                   p.project_id AS projectId,
                   p.title AS title,
                   p.procure_method AS procureMethod,
                   p.budget_amount AS budgetAmount,
                   p.status AS status,
                   GREATEST(
                     p.created_at,
                     COALESCE((SELECT MAX(e.created_at) FROM proc_entry e WHERE e.proc_project_id = p.id AND e.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(t.created_at) FROM proc_tender t WHERE t.proc_project_id = p.id AND t.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(b.created_at) FROM proc_bid b WHERE b.proc_project_id = p.id AND b.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(ev.created_at) FROM proc_evaluation ev WHERE ev.proc_project_id = p.id AND ev.deleted = 0), p.created_at),
                     COALESCE(a.created_at, p.created_at)
                   ) AS lastProgressAt,
                   a.award_code AS awardCode,
                   a.winner_supplier_name AS winnerSupplierName,
                   a.award_amount AS awardAmount,
                   (CASE WHEN a.id IS NOT NULL AND p.budget_amount > 0
                         THEN (a.award_amount - p.budget_amount) / p.budget_amount * 100 END) AS awardDeviationPct,
                   DATEDIFF(a.created_at, (SELECT MAX(t3.publish_time) FROM proc_tender t3
                                           WHERE t3.proc_project_id = p.id AND t3.deleted = 0)) AS cycleDays,
                   (CASE WHEN EXISTS (SELECT 1 FROM proc_plan_item pi
                                      JOIN proc_requirement r ON r.id = pi.requirement_id AND r.deleted = 0
                                      WHERE pi.id = p.plan_item_id AND pi.deleted = 0) THEN 1 ELSE 0 END) AS reqDone,
                   (CASE WHEN p.plan_item_id IS NOT NULL THEN 1 ELSE 0 END) AS planDone,
                   (CASE WHEN p.status IN ('APPROVED','ENTRY','TENDERING','EVALUATING','AWARDED','CLOSED') THEN 1 ELSE 0 END) AS projDone,
                   (CASE WHEN EXISTS (SELECT 1 FROM proc_entry e WHERE e.proc_project_id = p.id AND e.deleted = 0) THEN 1 ELSE 0 END) AS entryDone,
                   (CASE WHEN EXISTS (SELECT 1 FROM proc_tender t WHERE t.proc_project_id = p.id AND t.deleted = 0) THEN 1 ELSE 0 END) AS tenderDone,
                   (CASE WHEN EXISTS (SELECT 1 FROM proc_bid b WHERE b.proc_project_id = p.id AND b.deleted = 0) THEN 1 ELSE 0 END) AS bidDone,
                   (CASE WHEN EXISTS (SELECT 1 FROM proc_evaluation ev WHERE ev.proc_project_id = p.id AND ev.deleted = 0) THEN 1 ELSE 0 END) AS evalDone,
                   (CASE WHEN a.id IS NOT NULL AND a.status = 'APPROVED' THEN 1 ELSE 0 END) AS awardDone,
                   (CASE WHEN a.id IS NOT NULL AND (a.contract_generated = 1 OR a.contract_id IS NOT NULL) THEN 1 ELSE 0 END) AS contractDone
            FROM proc_project p
            LEFT JOIN proc_award a ON a.id = (SELECT MAX(a2.id) FROM proc_award a2
                                             WHERE a2.proc_project_id = p.id AND a2.deleted = 0)
            WHERE p.deleted = 0
            <if test="projectId != null"> AND p.project_id = #{projectId} </if>
            <if test="method != null and method != ''"> AND p.procure_method = #{method} </if>
            <if test="status != null and status != ''"> AND p.status = #{status} </if>
            <if test="kw != null and kw != ''"> AND (p.title LIKE CONCAT('%', #{kw}, '%')
                                                       OR p.project_code LIKE CONCAT('%', #{kw}, '%')) </if>
            ORDER BY p.id DESC
            </script>
            """)
        List<Map<String, Object>> selectOverviewPage(Page<?> page, @Param("projectId") Long projectId,
                                                     @Param("method") String method, @Param("status") String status,
                                                     @Param("kw") String kw);

        /** 立项方式分布（公开招标占比用） */
        @Select("""
            SELECT COUNT(*) AS totalCount,
                   COALESCE(SUM(CASE WHEN procure_method = 'PUBLIC_BID' THEN 1 ELSE 0 END), 0) AS publicBidCount
            FROM proc_project
            WHERE deleted = 0 AND project_id = #{projectId}
            """)
        Map<String, Object> selectMethodStats(@Param("projectId") Long projectId);

        /** 定标统计：平均采购周期（定标时间-发标时间）、平均偏差率 */
        @Select("""
            SELECT COUNT(*) AS awardCount,
                   COALESCE(AVG(DATEDIFF(a.created_at, t.publish_time)), 0) AS avgCycleDays,
                   COALESCE(AVG(CASE WHEN p.budget_amount > 0
                                     THEN (a.award_amount - p.budget_amount) / p.budget_amount * 100 END), 0) AS avgDeviationPct
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            LEFT JOIN proc_tender t ON t.id = (SELECT MAX(t2.id) FROM proc_tender t2
                                              WHERE t2.proc_project_id = p.id AND t2.deleted = 0)
            WHERE a.deleted = 0 AND a.status = 'APPROVED' AND p.project_id = #{projectId}
            """)
        Map<String, Object> selectAwardStats(@Param("projectId") Long projectId);

        /** 已审批直接委托金额合计（阳光度） */
        @Select("SELECT COALESCE(SUM(amount), 0) FROM proc_direct WHERE deleted = 0 AND status = 'APPROVED' AND project_id = #{projectId}")
        BigDecimal sumDirectAmount(@Param("projectId") Long projectId);

        /** 已审批定标金额合计（阳光度） */
        @Select("""
            SELECT COALESCE(SUM(a.award_amount), 0)
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            WHERE a.deleted = 0 AND a.status = 'APPROVED' AND p.project_id = #{projectId}
            """)
        BigDecimal sumAwardAmount(@Param("projectId") Long projectId);
    }

    /** 招采驾驶舱：KPI / 分布 / 周期 / 偏差 / 供应商 / 预警（全局口径，不区分项目） */
    @Mapper
    interface CockpitMapper {

        /** KPI 基础：立项总数/在招/已定标/待审/预算总额/招标方式计数 */
        @Select("""
            SELECT COUNT(*) AS projectCount,
                   COALESCE(SUM(CASE WHEN status IN ('APPROVING','APPROVED','ENTRY','TENDERING','EVALUATING') THEN 1 ELSE 0 END),0) AS inProgressCount,
                   COALESCE(SUM(CASE WHEN status IN ('AWARDED','CLOSED') THEN 1 ELSE 0 END),0) AS awardedCount,
                   COALESCE(SUM(CASE WHEN status IN ('DRAFT','APPROVING','REJECTED') THEN 1 ELSE 0 END),0) AS pendingCount,
                   COALESCE(SUM(budget_amount),0) AS budgetTotal,
                   COALESCE(SUM(CASE WHEN procure_method = 'PUBLIC_BID' THEN 1 ELSE 0 END),0) AS publicBidCount,
                   COALESCE(SUM(CASE WHEN procure_method = 'DIRECT' THEN 1 ELSE 0 END),0) AS directCount
            FROM proc_project WHERE deleted = 0
            """)
        Map<String, Object> selectBaseCards();

        /** KPI 定标：定标总额/定标数/平均周期/平均偏差率 */
        @Select("""
            SELECT COALESCE(SUM(a.award_amount),0) AS awardedTotal,
                   COUNT(*) AS awardCount,
                   COALESCE(AVG(DATEDIFF(a.created_at, t.publish_time)),0) AS avgCycleDays,
                   COALESCE(AVG(CASE WHEN p.budget_amount > 0
                                     THEN (a.award_amount - p.budget_amount) / p.budget_amount * 100 END),0) AS avgDeviationPct
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            LEFT JOIN proc_tender t ON t.id = (SELECT MAX(t2.id) FROM proc_tender t2
                                              WHERE t2.proc_project_id = p.id AND t2.deleted = 0)
            WHERE a.deleted = 0 AND a.status = 'APPROVED'
            """)
        Map<String, Object> selectAwardCards();

        /** KPI 围串标预警条数 */
        @Select("SELECT COUNT(*) FROM proc_collusion_warn")
        long selectCollusionCount();

        /** KPI 超期立项数（滞留天数 > days） */
        @Select("""
            SELECT COUNT(*) FROM (
              SELECT GREATEST(p.created_at,
                     COALESCE((SELECT MAX(e.created_at) FROM proc_entry e WHERE e.proc_project_id = p.id AND e.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(t.created_at) FROM proc_tender t WHERE t.proc_project_id = p.id AND t.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(b.created_at) FROM proc_bid b WHERE b.proc_project_id = p.id AND b.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(ev.created_at) FROM proc_evaluation ev WHERE ev.proc_project_id = p.id AND ev.deleted = 0), p.created_at),
                     COALESCE((SELECT MAX(a.created_at) FROM proc_award a WHERE a.proc_project_id = p.id AND a.deleted = 0), p.created_at)
                   ) AS lastProgressAt
              FROM proc_project p
              WHERE p.deleted = 0 AND p.status IN ('APPROVING','APPROVED','ENTRY','TENDERING','EVALUATING')
            ) x WHERE DATEDIFF(NOW(), lastProgressAt) > #{days}
            """)
        long selectOverdueCount(@Param("days") int days);

        /** 采购方式分布 */
        @Select("SELECT procure_method AS method, COUNT(*) AS count FROM proc_project WHERE deleted = 0 GROUP BY procure_method")
        List<Map<String, Object>> selectMethodDist();

        /** 阶段分布（按立项状态） */
        @Select("SELECT status, COUNT(*) AS count FROM proc_project WHERE deleted = 0 GROUP BY status")
        List<Map<String, Object>> selectStageDist();

        /** 采购周期趋势（按定标月份） */
        @Select("""
            SELECT DATE_FORMAT(a.created_at, '%Y-%m') AS month,
                   COUNT(*) AS awardCount,
                   COALESCE(AVG(DATEDIFF(a.created_at, t.publish_time)),0) AS avgCycleDays
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            LEFT JOIN proc_tender t ON t.id = (SELECT MAX(t2.id) FROM proc_tender t2
                                              WHERE t2.proc_project_id = p.id AND t2.deleted = 0)
            WHERE a.deleted = 0 AND a.status = 'APPROVED'
            GROUP BY DATE_FORMAT(a.created_at, '%Y-%m') ORDER BY month
            """)
        List<Map<String, Object>> selectCycleTrend();

        /** 预算 vs 定标偏差（每条已定标） */
        @Select("""
            SELECT p.title AS name, p.budget_amount AS budget, a.award_amount AS award,
                   CASE WHEN p.budget_amount > 0
                        THEN (a.award_amount - p.budget_amount) / p.budget_amount * 100 ELSE 0 END AS deviationPct
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            WHERE a.deleted = 0 AND a.status = 'APPROVED'
            ORDER BY a.award_amount DESC
            """)
        List<Map<String, Object>> selectDeviation();

        /** 中标供应商 Top */
        @Select("""
            SELECT winner_supplier_name AS name, COUNT(*) AS count, COALESCE(SUM(award_amount),0) AS amount
            FROM proc_award
            WHERE deleted = 0 AND status = 'APPROVED' AND winner_supplier_name IS NOT NULL
            GROUP BY winner_supplier_name ORDER BY amount DESC LIMIT 8
            """)
        List<Map<String, Object>> selectSupplierTop();

        /** 超期立项清单（预警用） */
        @Select("""
            SELECT procProjectId, projectCode, title, status, lastProgressAt FROM (
              SELECT p.id AS procProjectId, p.project_code AS projectCode, p.title, p.status,
                     GREATEST(p.created_at,
                       COALESCE((SELECT MAX(e.created_at) FROM proc_entry e WHERE e.proc_project_id = p.id AND e.deleted = 0), p.created_at),
                       COALESCE((SELECT MAX(t.created_at) FROM proc_tender t WHERE t.proc_project_id = p.id AND t.deleted = 0), p.created_at),
                       COALESCE((SELECT MAX(b.created_at) FROM proc_bid b WHERE b.proc_project_id = p.id AND b.deleted = 0), p.created_at),
                       COALESCE((SELECT MAX(ev.created_at) FROM proc_evaluation ev WHERE ev.proc_project_id = p.id AND ev.deleted = 0), p.created_at),
                       COALESCE((SELECT MAX(a.created_at) FROM proc_award a WHERE a.proc_project_id = p.id AND a.deleted = 0), p.created_at)
                     ) AS lastProgressAt
              FROM proc_project p
              WHERE p.deleted = 0 AND p.status IN ('APPROVING','APPROVED','ENTRY','TENDERING','EVALUATING')
            ) x WHERE DATEDIFF(NOW(), lastProgressAt) > #{days} ORDER BY lastProgressAt ASC LIMIT 10
            """)
        List<Map<String, Object>> selectOverdueList(@Param("days") int days);

        /** 定标超预算清单（预警用） */
        @Select("""
            SELECT p.title AS name, p.budget_amount AS budget, a.award_amount AS award,
                   (a.award_amount - p.budget_amount) / p.budget_amount * 100 AS deviationPct
            FROM proc_award a
            JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
            WHERE a.deleted = 0 AND a.status = 'APPROVED' AND a.award_amount > p.budget_amount
            ORDER BY deviationPct DESC
            """)
        List<Map<String, Object>> selectBudgetOverrun();

        /** 围串标预警清单（预警用） */
        @Select("""
            SELECT w.proc_project_id AS procProjectId, w.rule_code AS ruleCode, w.detail,
                   w.risk_level AS riskLevel, p.project_code AS projectCode, p.title
            FROM proc_collusion_warn w
            LEFT JOIN proc_project p ON p.id = w.proc_project_id
            ORDER BY w.created_at DESC
            """)
        List<Map<String, Object>> selectCollusionList();
    }
}
