package com.reip.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 三级驾驶舱 + 专题 + 下钻 聚合查询（跨 sale/cost/expense/plan/proc/invest 只读）
 * 说明：区域口径 = 组织本身 + 其直接子组织（org_id 匹配或父组织匹配），适配 集团→区域→城市公司 层级。
 */
@Mapper
public interface CockpitV2Mapper {

    // ==================== 通用（集团/区域/项目共用） ====================

    /** 签约额：Σ sale_contract.contract_price（生效/网签） */
    @Select("SELECT COALESCE(SUM(contract_price),0) FROM sale_contract WHERE deleted=0 AND status IN ('ACTIVE','FILED')")
    BigDecimal sumSignedAmount();

    /** 回款额：Σ sale_receipt.received_amount（实收） */
    @Select("SELECT COALESCE(SUM(received_amount),0) FROM sale_receipt WHERE deleted=0")
    BigDecimal sumReceiptAmount();

    /** 供货货值：Σ inv_value.total_value */
    @Select("SELECT COALESCE(SUM(total_value),0) FROM inv_value WHERE deleted=0")
    BigDecimal sumSupplyValue();

    /** 总货值（拿地锁定基线）：最新锁定 inv_calc_version.sale_value 之和 */
    @Select("""
        SELECT COALESCE(SUM(v.sale_value),0)
        FROM inv_calc_version v
        JOIN (SELECT project_id, MAX(id) mid FROM inv_calc_version WHERE locked=1 AND deleted=0 GROUP BY project_id) t
          ON t.mid=v.id
        """)
    BigDecimal sumTotalValue();

    /** 动态成本：每项目最新 cost_dynamic_snapshot.dynamic_total 之和 */
    @Select("""
        SELECT COALESCE(SUM(s.dynamic_total),0)
        FROM cost_dynamic_snapshot s
        JOIN (SELECT project_id, MAX(snap_month) m FROM cost_dynamic_snapshot GROUP BY project_id) t
          ON t.project_id=s.project_id AND t.m=s.snap_month
        """)
    BigDecimal sumLatestDynamicCost();

    /** 动态利润：每项目最新 inv_dynamic_profit.dynamic_profit 之和 */
    @Select("""
        SELECT COALESCE(SUM(p.dynamic_profit),0)
        FROM inv_dynamic_profit p
        JOIN (SELECT project_id, MAX(snap_month) m FROM inv_dynamic_profit GROUP BY project_id) t
          ON t.project_id=p.project_id AND t.m=p.snap_month
        """)
    BigDecimal sumLatestDynamicProfit();

    /** 实际成本：Σ cost_payment_apply.paid_amount（已实付） */
    @Select("""
        <script>
        SELECT COALESCE(SUM(pa.paid_amount),0) FROM cost_payment_apply pa
        WHERE pa.deleted=0 AND pa.status='PAID'
        <if test="orgId != null">
          AND pa.project_id IN (SELECT id FROM mdm_project WHERE deleted=0
            AND (org_id=#{orgId} OR org_id IN (SELECT id FROM mdm_org WHERE parent_id=#{orgId} AND deleted=0)))
        </if>
        </script>
        """)
    BigDecimal sumActualCost(@Param("orgId") Long orgId);

    /**
     * 每项目全指标（集团/区域/项目地图共用）。
     * orgId=null 全集团；orgId 非空则限定 组织+直接子组织 下的项目。
     */
    @Select("""
        <script>
        SELECT p.id AS projectId, p.name AS projectName, p.org_id AS orgId, o.name AS orgName, p.city, p.status AS projectStatus,
          (SELECT COALESCE(SUM(CASE WHEN t2.alert_status='RED' THEN 1 ELSE 0 END),0)   FROM plan_task t2 WHERE t2.project_id=p.id AND t2.deleted=0) AS planRed,
          (SELECT COALESCE(SUM(CASE WHEN t2.alert_status='YELLOW' THEN 1 ELSE 0 END),0) FROM plan_task t2 WHERE t2.project_id=p.id AND t2.deleted=0) AS planYellow,
          (SELECT COALESCE(SUM(CASE WHEN t2.status='COMPLETED' THEN 1 ELSE 0 END),0)   FROM plan_task t2 WHERE t2.project_id=p.id AND t2.deleted=0) AS planCompleted,
          (SELECT COUNT(*) FROM plan_task t2 WHERE t2.project_id=p.id AND t2.deleted=0) AS planTotal,
          (SELECT ds.target_total  FROM cost_dynamic_snapshot ds WHERE ds.project_id=p.id ORDER BY ds.snap_month DESC LIMIT 1) AS costTarget,
          (SELECT ds.dynamic_total FROM cost_dynamic_snapshot ds WHERE ds.project_id=p.id ORDER BY ds.snap_month DESC LIMIT 1) AS costDynamic,
          (SELECT dp.dynamic_profit FROM inv_dynamic_profit dp WHERE dp.project_id=p.id ORDER BY dp.snap_month DESC LIMIT 1) AS dynamicProfit,
          (SELECT dp.dynamic_value FROM inv_dynamic_profit dp WHERE dp.project_id=p.id ORDER BY dp.snap_month DESC LIMIT 1) AS dynamicValue,
          (SELECT v.net_profit FROM inv_calc_version v WHERE v.project_id=p.id AND v.locked=1 AND v.deleted=0 ORDER BY v.id DESC LIMIT 1) AS baselineProfit,
          (SELECT COALESCE(SUM(sc.contract_price),0) FROM sale_contract sc WHERE sc.project_id=p.id AND sc.deleted=0 AND sc.status IN ('ACTIVE','FILED')) AS signedAmount,
          (SELECT COALESCE(SUM(sr.received_amount),0) FROM sale_receipt sr JOIN sale_contract sc ON sc.id=sr.contract_id WHERE sc.project_id=p.id AND sc.deleted=0 AND sr.deleted=0) AS receiptAmount,
          (SELECT COALESCE(SUM(iv.total_value),0) FROM inv_value iv WHERE iv.project_id=p.id AND iv.deleted=0) AS supplyValue,
          (SELECT COALESCE(SUM(CASE WHEN ssr.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN ssr.table_price ELSE 0 END),0) FROM sale_room_status ssr WHERE ssr.project_id=p.id AND ssr.deleted=0) AS soldValue,
          (SELECT COALESCE(SUM(ssr.table_price),0) FROM sale_room_status ssr WHERE ssr.project_id=p.id AND ssr.deleted=0) AS totalRoomValue,
          (SELECT COALESCE(SUM(CASE WHEN ssr.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN 1 ELSE 0 END),0) FROM sale_room_status ssr WHERE ssr.project_id=p.id AND ssr.deleted=0) AS soldRooms,
          (SELECT COUNT(*) FROM sale_room_status ssr WHERE ssr.project_id=p.id AND ssr.deleted=0) AS totalRooms
        FROM mdm_project p LEFT JOIN mdm_org o ON o.id=p.org_id AND o.deleted=0
        WHERE p.deleted=0
        <if test="orgId != null">
          AND (p.org_id=#{orgId} OR p.org_id IN (SELECT id FROM mdm_org WHERE parent_id=#{orgId} AND deleted=0))
        </if>
        ORDER BY p.id
        </script>
        """)
    List<Map<String, Object>> selectProjectMetrics(@Param("orgId") Long orgId);

    /** 组织列表（区域下钻候选：区域 + 城市公司） */
    @Select("SELECT id, name, code, org_type AS orgType FROM mdm_org WHERE deleted=0 AND org_type IN ('REGION','CITY') ORDER BY id")
    List<Map<String, Object>> selectRegions();

    // ==================== 集团 · 风险预警聚合 ====================

    /** 计划红灯里程碑 */
    @Select("""
        SELECT p.id AS projectId, p.name AS projectName, t.task_name AS taskName, t.alert_status AS alertStatus, t.plan_end AS planEnd
        FROM plan_task t JOIN mdm_project p ON p.id=t.project_id AND p.deleted=0
        WHERE t.deleted=0 AND t.alert_status='RED' AND (t.is_milestone=1 OR t.level='里程碑')
        ORDER BY t.id DESC
        """)
    List<Map<String, Object>> selectRedMilestones();

    /** 招采围串标预警 */
    @Select("""
        SELECT pp.project_id AS projectId, pp.title, c.rule_code AS ruleCode, c.detail, c.risk_level AS riskLevel
        FROM proc_collusion_warn c JOIN proc_project pp ON pp.id=c.proc_project_id AND pp.deleted=0
        ORDER BY c.id DESC
        """)
    List<Map<String, Object>> selectCollusionWarnings();

    /** 费用超预算（余额为负） */
    @Select("""
        SELECT o.name AS deptName, b.account_name AS accountName, b.amount AS budget,
               b.occupy_amount AS occupy, b.writeoff_amount AS writeoff, b.balance
        FROM exp_budget b LEFT JOIN mdm_org o ON o.id=b.dept_id AND o.deleted=0
        WHERE b.deleted=0 AND b.balance < 0
        """)
    List<Map<String, Object>> selectOverBudgetExpense();

    // ==================== 区域 · 费效 / 招采阳光度 ====================

    /** 区域三费构成（组织子树） */
    @Select("""
        <script>
        SELECT
          COALESCE(SUM(CASE WHEN i.account_name LIKE '%销售%' OR i.account_name LIKE '%市场%' OR i.account_name LIKE '%推广%' THEN i.amount ELSE 0 END),0) AS salesFee,
          COALESCE(SUM(CASE WHEN i.account_name LIKE '%财务%' OR i.account_name LIKE '%利息%' THEN i.amount ELSE 0 END),0) AS finFee,
          COALESCE(SUM(i.amount),0) AS total
        FROM exp_reimburse_item i JOIN exp_reimburse r ON r.id=i.reimb_id AND r.deleted=0
        WHERE i.deleted=0 AND r.status IN ('APPROVED','PAID')
        <if test="orgId != null">
          AND (r.dept_id=#{orgId} OR r.dept_id IN (SELECT id FROM mdm_org WHERE parent_id=#{orgId} AND deleted=0))
        </if>
        </script>
        """)
    Map<String, Object> regionFee(@Param("orgId") Long orgId);

    /** 区域招采阳光度（公开招标占比 + 定标/立项金额比） */
    @Select("""
        <script>
        SELECT COUNT(*) AS totalCount,
          COALESCE(SUM(CASE WHEN p.procure_method='PUBLIC_BID' THEN 1 ELSE 0 END),0) AS publicBidCount,
          COALESCE(SUM(p.budget_amount),0) AS budgetAmount,
          COALESCE((SELECT SUM(a.award_amount) FROM proc_award a JOIN proc_project pp ON pp.id=a.proc_project_id AND pp.deleted=0
            WHERE a.deleted=0 AND a.status='APPROVED'
            <if test="orgId != null">
              AND pp.project_id IN (SELECT id FROM mdm_project WHERE deleted=0
                AND (org_id=#{orgId} OR org_id IN (SELECT id FROM mdm_org WHERE parent_id=#{orgId} AND deleted=0)))
            </if>),0) AS awardAmount
        FROM proc_project p WHERE p.deleted=0
        <if test="orgId != null">
          AND p.project_id IN (SELECT id FROM mdm_project WHERE deleted=0
            AND (org_id=#{orgId} OR org_id IN (SELECT id FROM mdm_org WHERE parent_id=#{orgId} AND deleted=0)))
        </if>
        </script>
        """)
    Map<String, Object> regionProcurement(@Param("orgId") Long orgId);

    // ==================== 项目 · 全周期 / 三算 / 三台账 ====================

    /** 全周期里程碑时间轴（拿地→…→交付） */
    @Select("""
        SELECT id, task_code AS taskCode, task_name AS taskName, level, is_milestone AS isMilestone, specialty,
               plan_start AS planStart, plan_end AS planEnd, baseline_end AS baselineEnd,
               actual_start AS actualStart, actual_end AS actualEnd, progress_pct AS progressPct,
               alert_status AS alertStatus, status
        FROM plan_task WHERE deleted=0 AND project_id=#{projectId} AND (level='里程碑' OR is_milestone=1)
        ORDER BY plan_start, id
        """)
    List<Map<String, Object>> selectProjectTimeline(@Param("projectId") Long projectId);

    /** 货值供销存月度流水 */
    @Select("SELECT flow_month AS flowMonth, supply_value AS supplyValue, sold_value AS soldValue, "
            + "refund_value AS refundValue, stock_value AS stockValue FROM inv_value_flow "
            + "WHERE project_id=#{projectId} ORDER BY flow_month")
    List<Map<String, Object>> selectValueFlow(@Param("projectId") Long projectId);

    /** 货值台账（按业态） */
    @Select("SELECT product_category AS productCategory, area, price, total_value AS totalValue, supply_date AS supplyDate "
            + "FROM inv_value WHERE deleted=0 AND project_id=#{projectId} ORDER BY id")
    List<Map<String, Object>> selectValueLedger(@Param("projectId") Long projectId);

    /** 目标成本科目单方（最新 APPROVED/LOCKED 版本） */
    @Select("""
        SELECT i.account_name AS accountName, i.account_code AS accountCode,
               COALESCE(SUM(i.amount),0) AS amount, COALESCE(MAX(i.unit_cost),0) AS unitCost
        FROM cost_target_item i JOIN cost_target_version v ON v.id=i.version_id
        WHERE i.deleted=0 AND v.project_id=#{projectId} AND v.status IN ('APPROVED','LOCKED')
        GROUP BY i.account_name, i.account_code ORDER BY amount DESC
        """)
    List<Map<String, Object>> selectCostByAccount(@Param("projectId") Long projectId);

    /** 无效成本明细 */
    @Select("SELECT invalid_type AS invalidType, amount, occurred_date AS occurredDate, description "
            + "FROM cost_invalid WHERE deleted=0 AND project_id=#{projectId} ORDER BY occurred_date DESC")
    List<Map<String, Object>> selectInvalidCost(@Param("projectId") Long projectId);

    /** 红黄灯分布 */
    @Select("SELECT alert_status AS alertStatus, COUNT(*) AS cnt FROM plan_task "
            + "WHERE deleted=0 AND project_id=#{projectId} GROUP BY alert_status")
    List<Map<String, Object>> selectAlertDistribution(@Param("projectId") Long projectId);

    /** 合同台账速览（生效/结算） */
    @Select("SELECT COALESCE(COUNT(*),0) AS cnt, COALESCE(SUM(signed_amount),0) AS amount FROM cost_contract "
            + "WHERE deleted=0 AND project_id=#{projectId} AND status IN ('EFFECTIVE','SETTLED')")
    Map<String, Object> selectContractLedger(@Param("projectId") Long projectId);

    /** 变更台账速览（已确认） */
    @Select("SELECT COALESCE(COUNT(*),0) AS cnt, COALESCE(SUM(confirmed_amount),0) AS amount FROM cost_change "
            + "WHERE deleted=0 AND project_id=#{projectId} AND status='CONFIRMED'")
    Map<String, Object> selectChangeLedger(@Param("projectId") Long projectId);

    /** 付款台账速览（已实付） */
    @Select("SELECT COALESCE(COUNT(*),0) AS cnt, COALESCE(SUM(paid_amount),0) AS amount FROM cost_payment_apply "
            + "WHERE deleted=0 AND project_id=#{projectId} AND status='PAID'")
    Map<String, Object> selectPaymentLedger(@Param("projectId") Long projectId);

    // ==================== 专题分析 ====================

    /** 货值专题：业态分布 */
    @Select("SELECT product_category AS productCategory, COALESCE(SUM(total_value),0) AS totalValue, "
            + "COALESCE(SUM(area),0) AS area FROM inv_value WHERE deleted=0 GROUP BY product_category ORDER BY totalValue DESC")
    List<Map<String, Object>> selectValueDistribution();

    /** 货值专题：签约月度去化速度 */
    @Select("SELECT DATE_FORMAT(signed_at,'%Y-%m') AS month, COALESCE(SUM(contract_price),0) AS amount, COUNT(*) AS cnt "
            + "FROM sale_contract WHERE deleted=0 AND status IN ('ACTIVE','FILED') GROUP BY month ORDER BY month")
    List<Map<String, Object>> selectSaleTrend();

    /** 成本专题：变更率排行（确认变更/已签合同） */
    @Select("""
        SELECT p.id AS projectId, p.name AS projectName,
          COALESCE((SELECT SUM(c.confirmed_amount) FROM cost_change c WHERE c.project_id=p.id AND c.deleted=0 AND c.status='CONFIRMED'),0) AS confirmedChange,
          COALESCE((SELECT SUM(cc.signed_amount) FROM cost_contract cc WHERE cc.project_id=p.id AND cc.deleted=0 AND cc.status IN ('EFFECTIVE','SETTLED')),0) AS contracted
        FROM mdm_project p WHERE p.deleted=0 ORDER BY confirmedChange DESC
        """)
    List<Map<String, Object>> selectChangeRateRanking();

    /** 销售专题：价格实现率（成交均价/表价均价） */
    @Select("SELECT COALESCE(AVG(contract_price),0) AS avgContract FROM sale_contract WHERE deleted=0 AND status IN ('ACTIVE','FILED')")
    BigDecimal selectAvgContractPrice();

    @Select("SELECT COALESCE(AVG(table_price),0) AS avgTable FROM sale_room_status WHERE deleted=0")
    BigDecimal selectAvgTablePrice();

    /** 销售专题：渠道效力（按中介/供应商） */
    @Select("SELECT COALESCE(supplier_name,'自然到访') AS supplier, COUNT(*) AS cnt, COALESCE(SUM(contract_price),0) AS amount "
            + "FROM sale_contract WHERE deleted=0 AND status IN ('ACTIVE','FILED') GROUP BY supplier ORDER BY amount DESC")
    List<Map<String, Object>> selectChannelEffect();

    /** 招采专题：定标价偏差 + 周期 */
    @Select("""
        SELECT p.project_id AS projectId, p.title, p.budget_amount AS budgetAmount,
               a.award_amount AS awardAmount, (a.award_amount - p.budget_amount) AS deviation,
               a.deviation_pct AS deviationPct, DATEDIFF(a.created_at, t.publish_time) AS cycleDays
        FROM proc_project p
        LEFT JOIN proc_award a ON a.id=(SELECT MAX(a2.id) FROM proc_award a2 WHERE a2.proc_project_id=p.id AND a2.deleted=0)
        LEFT JOIN proc_tender t ON t.id=(SELECT MAX(t2.id) FROM proc_tender t2 WHERE t2.proc_project_id=p.id AND t2.deleted=0)
        WHERE p.deleted=0 ORDER BY p.id DESC
        """)
    List<Map<String, Object>> selectProcDeviation();

    /** 招采专题：围串标统计（按规则） */
    @Select("SELECT rule_code AS ruleCode, risk_level AS riskLevel, COUNT(*) AS cnt "
            + "FROM proc_collusion_warn GROUP BY rule_code, risk_level ORDER BY cnt DESC")
    List<Map<String, Object>> selectCollusionStats();

    /** 费用专题：预算执行（部门视角） */
    @Select("SELECT o.name AS deptName, COALESCE(SUM(b.amount),0) AS budget, COALESCE(SUM(b.occupy_amount),0) AS occupy, "
            + "COALESCE(SUM(b.writeoff_amount),0) AS writeoff, COALESCE(SUM(b.balance),0) AS balance "
            + "FROM exp_budget b LEFT JOIN mdm_org o ON o.id=b.dept_id AND o.deleted=0 "
            + "WHERE b.deleted=0 GROUP BY o.name ORDER BY budget DESC")
    List<Map<String, Object>> selectBudgetExecByDept();

    // ==================== 下钻（汇总→明细穿透） ====================

    @Select("""
        <script>
        SELECT sc.id, sc.contract_no AS contractNo, sc.project_id AS projectId, p.name AS projectName,
               c.name AS customerName, sc.contract_price AS contractPrice, sc.signed_at AS signedAt, sc.status
        FROM sale_contract sc
        LEFT JOIN mdm_project p ON p.id=sc.project_id
        LEFT JOIN mdm_customer c ON c.id=sc.customer_id
        WHERE sc.deleted=0 AND sc.status IN ('ACTIVE','FILED')
        <if test="projectId != null"> AND sc.project_id=#{projectId} </if>
        ORDER BY sc.id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillContracts(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT sr.id, sr.receipt_no AS receiptNo, sr.received_amount AS receivedAmount, sr.pay_way AS payWay,
               sr.received_at AS receivedAt, sc.contract_no AS contractNo, p.name AS projectName
        FROM sale_receipt sr JOIN sale_contract sc ON sc.id=sr.contract_id
        LEFT JOIN mdm_project p ON p.id=sc.project_id
        WHERE sr.deleted=0
        <if test="projectId != null"> AND sc.project_id=#{projectId} </if>
        ORDER BY sr.id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillReceipts(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT cc.id, cc.contract_code AS contractCode, cc.name, cc.supplier_name AS supplierName,
               cc.signed_amount AS signedAmount, cc.status
        FROM cost_contract cc WHERE cc.deleted=0
        <if test="projectId != null"> AND cc.project_id=#{projectId} </if>
        ORDER BY cc.id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillCostContracts(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT id, room_code AS roomCode, building_id AS buildingId, area, table_price AS tablePrice, status
        FROM sale_room_status WHERE deleted=0
        <if test="projectId != null"> AND project_id=#{projectId} </if>
        ORDER BY id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillRooms(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT id, snap_month AS snapMonth, dynamic_value AS dynamicValue, dynamic_cost AS dynamicCost,
               three_fee AS threeFee, tax_amount AS taxAmount, dynamic_profit AS dynamicProfit
        FROM inv_dynamic_profit
        <if test="projectId != null"> WHERE project_id=#{projectId} </if>
        ORDER BY snap_month DESC LIMIT 24
        </script>
        """)
    List<Map<String, Object>> drillDynamicProfit(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT id, change_code AS changeCode, title, change_type AS changeType, confirmed_amount AS confirmedAmount,
               status FROM cost_change WHERE deleted=0
        <if test="projectId != null"> AND project_id=#{projectId} </if>
        ORDER BY id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillChanges(@Param("projectId") Long projectId);

    @Select("""
        <script>
        SELECT id, task_code AS taskCode, task_name AS taskName, level, alert_status AS alertStatus, status,
               plan_end AS planEnd FROM plan_task WHERE deleted=0
        <if test="projectId != null"> AND project_id=#{projectId} </if>
        ORDER BY id DESC LIMIT 200
        </script>
        """)
    List<Map<String, Object>> drillPlanTasks(@Param("projectId") Long projectId);

    @Select("SELECT i.id, r.reimb_no AS reimbNo, r.user_name AS userName, i.account_name AS accountName, i.amount "
            + "FROM exp_reimburse_item i JOIN exp_reimburse r ON r.id=i.reimb_id AND r.deleted=0 "
            + "WHERE i.deleted=0 AND r.status IN ('APPROVED','PAID') ORDER BY i.id DESC LIMIT 200")
    List<Map<String, Object>> drillExpenseItems();

    @Select("SELECT id, dept_id AS deptId, account_name AS accountName, amount AS budget, occupy_amount AS occupy, "
            + "writeoff_amount AS writeoff, balance FROM exp_budget WHERE deleted=0 ORDER BY id DESC LIMIT 200")
    List<Map<String, Object>> drillBudget();

    // ==================== 补充：项目级 / 集团专题 ====================

    /** 项目信息 */
    @Select("SELECT p.id, p.project_code AS projectCode, p.name, p.city, p.status, p.cooperation_mode AS cooperationMode, "
            + "o.name AS orgName, p.land_acquired_date AS landAcquiredDate, p.far_area AS farArea "
            + "FROM mdm_project p LEFT JOIN mdm_org o ON o.id=p.org_id AND o.deleted=0 WHERE p.id=#{projectId} AND p.deleted=0")
    Map<String, Object> selectProjectInfo(@Param("projectId") Long projectId);

    /** 项目最新动态利润（含三费/税金/基线） */
    @Select("SELECT snap_month AS snapMonth, dynamic_value AS dynamicValue, dynamic_cost AS dynamicCost, "
            + "three_fee AS threeFee, tax_amount AS taxAmount, dynamic_profit AS dynamicProfit, profit_rate AS profitRate, "
            + "baseline_profit AS baselineProfit "
            + "FROM inv_dynamic_profit WHERE project_id=#{projectId} ORDER BY snap_month DESC LIMIT 1")
    Map<String, Object> selectDynamicProfitLatest(@Param("projectId") Long projectId);

    /** 项目成本：目标/动态（最新快照） */
    @Select("SELECT target_total AS targetCost, dynamic_total AS dynamicCost, contracted_total AS contracted, "
            + "confirmed_change AS confirmedChange, estimated_change AS estimatedChange "
            + "FROM cost_dynamic_snapshot WHERE project_id=#{projectId} ORDER BY snap_month DESC LIMIT 1")
    Map<String, Object> selectProjectCost(@Param("projectId") Long projectId);

    /** 项目实际成本（已实付） */
    @Select("SELECT COALESCE(SUM(paid_amount),0) FROM cost_payment_apply WHERE deleted=0 AND project_id=#{projectId} AND status='PAID'")
    BigDecimal sumProjectPaid(@Param("projectId") Long projectId);

    /** 集团·成本专题：科目单方对比（跨项目） */
    @Select("SELECT i.account_name AS accountName, COALESCE(SUM(i.amount),0) AS amount, "
            + "COALESCE(SUM(i.unit_cost),0) AS unitCost "
            + "FROM cost_target_item i JOIN cost_target_version v ON v.id=i.version_id "
            + "WHERE i.deleted=0 AND v.status IN ('APPROVED','LOCKED') "
            + "GROUP BY i.account_name ORDER BY amount DESC")
    List<Map<String, Object>> selectGroupCostByAccount();

    /** 集团·成本专题：无效成本清单 */
    @Select("SELECT p.name AS projectName, c.invalid_type AS invalidType, c.amount, c.occurred_date AS occurredDate "
            + "FROM cost_invalid c JOIN mdm_project p ON p.id=c.project_id AND p.deleted=0 "
            + "WHERE c.deleted=0 ORDER BY c.occurred_date DESC")
    List<Map<String, Object>> selectGroupInvalidCost();
}
