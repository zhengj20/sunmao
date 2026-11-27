package com.reip.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 模块跨域只读查询（仅 SELECT，不写业务表）。
 * 审批预审/报告取数复用各模块已暴露的只读 Mapper，此处只补充少量聚合查询。
 */
@Mapper
public interface AiBizMapper {

    /** 相似单据：同项目已审批/生效销售合同（前 N 条，排除自身） */
    @Select("""
        SELECT sc.id, sc.contract_no AS contractNo, c.name AS customerName,
               sc.contract_price AS contractPrice, sc.discount_rate AS discountRate,
               sc.signed_at AS signedAt, sc.status
        FROM sale_contract sc LEFT JOIN mdm_customer c ON c.id = sc.customer_id AND c.deleted = 0
        WHERE sc.deleted = 0 AND sc.project_id = #{projectId} AND sc.id <> #{excludeId}
          AND sc.status IN ('ACTIVE','FILED')
        ORDER BY sc.signed_at DESC, sc.id DESC LIMIT #{limit}
        """)
    List<Map<String, Object>> similarContracts(@Param("projectId") Long projectId,
                                               @Param("excludeId") Long excludeId,
                                               @Param("limit") int limit);

    /** 供应商已过期资质数（expire_date < 今天） */
    @Select("SELECT COUNT(*) FROM mdm_supplier_qualification WHERE deleted = 0 "
            + "AND supplier_id = #{supplierId} AND expire_date IS NOT NULL AND expire_date < CURDATE()")
    long countExpiredQual(@Param("supplierId") Long supplierId);

    /** 报销单详情（审批预审） */
    @Select("""
        SELECT r.id, r.reimb_no AS reimbNo, r.type, r.user_name AS userName,
               r.total_amount AS totalAmount, r.over_amount AS overAmount, r.over_reason AS overReason, r.status
        FROM exp_reimburse r WHERE r.id = #{id} AND r.deleted = 0
        """)
    Map<String, Object> expReimburseById(@Param("id") Long id);

    /** 定标单详情（审批预审） */
    @Select("""
        SELECT a.id, p.title, p.budget_amount AS budgetAmount, a.award_amount AS awardAmount,
               a.supplier_id AS supplierId, a.supplier_name AS supplierName, a.status
        FROM proc_award a LEFT JOIN proc_project p ON p.id = a.proc_project_id AND p.deleted = 0
        WHERE a.id = #{id} AND a.deleted = 0
        """)
    Map<String, Object> procAwardById(@Param("id") Long id);

    /** 成本变更单详情 + 变更率（审批预审） */
    @Select("""
        SELECT c.id, c.change_code AS changeCode, c.title, c.confirmed_amount AS confirmedAmount, c.status,
               COALESCE(cc.signed_amount, 0) AS contractedAmount
        FROM cost_change c
        LEFT JOIN cost_contract cc ON cc.id = (SELECT cc2.id FROM cost_contract cc2
               WHERE cc2.project_id = c.project_id AND cc2.deleted = 0 AND cc2.status IN ('EFFECTIVE','SETTLED')
               ORDER BY cc2.id DESC LIMIT 1)
        WHERE c.id = #{id} AND c.deleted = 0
        """)
    Map<String, Object> costChangeById(@Param("id") Long id);
}
