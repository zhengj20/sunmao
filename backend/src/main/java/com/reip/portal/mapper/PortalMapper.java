package com.reip.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门户工作台聚合查询（跨域表只读）
 */
@Mapper
public interface PortalMapper {

    /** 待办审批数：我的任务或代理给我的 PENDING 任务 */
    @Select("""
        SELECT COUNT(*) FROM wfl_task
        WHERE deleted = 0 AND status = 'PENDING'
          AND (assignee_id = #{userId} OR agent_id = #{userId})
        """)
    long countTodo(Long userId);

    /** 在建项目数 */
    @Select("SELECT COUNT(*) FROM mdm_project WHERE deleted = 0 AND status = 'CONSTRUCTION'")
    long countConstructionProjects();

    /** 生效/已结算合同签约额合计 */
    @Select("SELECT COALESCE(SUM(signed_amount), 0) FROM cost_contract WHERE deleted = 0 AND status IN ('EFFECTIVE', 'SETTLED')")
    BigDecimal sumContractAmount();

    /** 已实付金额合计 */
    @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM cost_payment_apply WHERE deleted = 0 AND status = 'PAID'")
    BigDecimal sumPaymentAmount();

    /** 动态成本：每项目最新快照 dynamic_total 之和 */
    @Select("""
        SELECT COALESCE(SUM(s.dynamic_total), 0)
        FROM cost_dynamic_snapshot s
        JOIN (SELECT project_id, MAX(snap_month) AS m FROM cost_dynamic_snapshot GROUP BY project_id) t
          ON t.project_id = s.project_id AND t.m = s.snap_month
        """)
    BigDecimal sumLatestDynamicCost();

    /** 动态利润：每项目最新快照 dynamic_profit 之和 */
    @Select("""
        SELECT COALESCE(SUM(p.dynamic_profit), 0)
        FROM inv_dynamic_profit p
        JOIN (SELECT project_id, MAX(snap_month) AS m FROM inv_dynamic_profit GROUP BY project_id) t
          ON t.project_id = p.project_id AND t.m = p.snap_month
        """)
    BigDecimal sumLatestDynamicProfit();

    /** 当前货值合计 */
    @Select("SELECT COALESCE(SUM(total_value), 0) FROM inv_value WHERE deleted = 0")
    BigDecimal sumSaleValue();

    /** 我的草稿单据（合同/变更/付款/采购需求，取最近5张） */
    @Select("""
        SELECT * FROM (
            SELECT '合同' AS bizType, id AS bizId, name AS title, created_at AS createdAt
            FROM cost_contract WHERE deleted = 0 AND status = 'DRAFT' AND created_by = #{userId}
            UNION ALL
            SELECT '变更', id, title, created_at
            FROM cost_change WHERE deleted = 0 AND status = 'ESTIMATING' AND created_by = #{userId}
            UNION ALL
            SELECT '付款申请', id, payment_code, created_at
            FROM cost_payment_apply WHERE deleted = 0 AND status = 'DRAFT' AND created_by = #{userId}
            UNION ALL
            SELECT '采购需求', id, title, created_at
            FROM proc_requirement WHERE deleted = 0 AND status = 'DRAFT' AND created_by = #{userId}
        ) t
        ORDER BY createdAt DESC LIMIT 5
        """)
    List<Map<String, Object>> selectMyDrafts(Long userId);

    /** 最新领域事件（Outbox 留痕） */
    @Select("""
        SELECT topic, biz_type AS bizType, biz_id AS bizId, created_at AS createdAt
        FROM biz_event ORDER BY id DESC LIMIT 10
        """)
    List<Map<String, Object>> selectRecentEvents();
}
