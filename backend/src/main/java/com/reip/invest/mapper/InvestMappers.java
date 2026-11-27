package com.reip.invest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.invest.entity.InvCalcVersion;
import com.reip.invest.entity.InvDynamicProfit;
import com.reip.invest.entity.InvValue;
import com.reip.invest.entity.InvValueFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InvestMappers {

    @Mapper
    interface CalcVersionMapper extends BaseMapper<InvCalcVersion> {

        /** 取项目最新锁定版本的净利润（拿地基线） */
        @Select("SELECT net_profit FROM inv_calc_version WHERE project_id = #{projectId} AND locked = 1 AND deleted = 0 ORDER BY id DESC LIMIT 1")
        BigDecimal selectBaselineProfit(Long projectId);
    }

    @Mapper
    interface ValueMapper extends BaseMapper<InvValue> {

        /** 项目当前货值合计（动态货值来源） */
        @Select("SELECT COALESCE(SUM(total_value), 0) FROM inv_value WHERE project_id = #{projectId} AND deleted = 0")
        BigDecimal sumTotalValue(Long projectId);
    }

    @Mapper
    interface ValueFlowMapper extends BaseMapper<InvValueFlow> {

        @Select("SELECT COALESCE(SUM(supply_value), 0) FROM inv_value_flow WHERE project_id = #{projectId}")
        BigDecimal sumSupply(Long projectId);

        @Select("SELECT COALESCE(SUM(sold_value), 0) FROM inv_value_flow WHERE project_id = #{projectId}")
        BigDecimal sumSold(Long projectId);

        @Select("SELECT COALESCE(SUM(refund_value), 0) FROM inv_value_flow WHERE project_id = #{projectId}")
        BigDecimal sumRefund(Long projectId);
    }

    @Mapper
    interface DynamicProfitMapper extends BaseMapper<InvDynamicProfit> {
    }

    /** 动态成本快照查询（成本域表，跨模块只读） */
    @Mapper
    interface CostSnapshotMapper {

        @Select("SELECT dynamic_total FROM cost_dynamic_snapshot WHERE project_id = #{projectId} ORDER BY snap_month DESC LIMIT 1")
        BigDecimal selectLatestDynamicTotal(Long projectId);
    }

    /** 投资总览 / 驾驶舱：组合只读聚合（基线测算 + 动态利润 + 货值 + 供销存 + 项目主数据） */
    @Mapper
    interface OverviewMapper {

        /** 每项目基线：优先最新 LOCKED 版本，无锁定则取最新版本 */
        @Select("""
            SELECT v.project_id AS projectId, v.version_code AS versionCode, v.stage, v.locked, v.status,
                   v.sale_value AS saleValue, v.total_cost AS totalCost,
                   v.net_profit AS netProfit, v.profit_rate AS profitRate, v.irr, v.payback_years AS paybackYears
            FROM inv_calc_version v
            JOIN (
                SELECT COALESCE(
                         (SELECT MAX(id) FROM inv_calc_version c2 WHERE c2.project_id = c1.project_id AND c2.locked = 1 AND c2.deleted = 0),
                         (SELECT MAX(id) FROM inv_calc_version c3 WHERE c3.project_id = c1.project_id AND c3.deleted = 0)
                     ) AS target_id
                FROM (SELECT DISTINCT project_id FROM inv_calc_version WHERE deleted = 0) c1
            ) t ON t.target_id = v.id
            """)
        java.util.List<java.util.Map<String, Object>> selectBaselinePerProject();

        /** 每项目最新动态利润 */
        @Select("""
            SELECT dp.project_id AS projectId, dp.snap_month AS snapMonth,
                   dp.dynamic_value AS dynamicValue, dp.dynamic_cost AS dynamicCost,
                   dp.dynamic_profit AS dynamicProfit, dp.profit_rate AS profitRate
            FROM inv_dynamic_profit dp
            JOIN (SELECT project_id AS pid, MAX(snap_month) AS m FROM inv_dynamic_profit GROUP BY project_id) l
              ON l.pid = dp.project_id AND l.m = dp.snap_month
            """)
        java.util.List<java.util.Map<String, Object>> selectLatestDynamicPerProject();

        /** 项目主数据（状态 / 城市 / 土地款） */
        @Select("""
            SELECT id, project_code AS projectCode, name, city, status, land_cost AS landCost
            FROM mdm_project WHERE deleted = 0 ORDER BY id
            """)
        java.util.List<java.util.Map<String, Object>> selectProjects();

        /** 业态货值合计（inv_value） */
        @Select("""
            SELECT product_category AS category, COALESCE(SUM(total_value), 0) AS value
            FROM inv_value WHERE deleted = 0 GROUP BY product_category
            """)
        java.util.List<java.util.Map<String, Object>> selectCategoryValue();

        /** 动态利润月度趋势（跨项目合计） */
        @Select("""
            SELECT snap_month AS month,
                   COALESCE(SUM(dynamic_profit), 0) AS dynamicProfit,
                   COALESCE(SUM(dynamic_cost), 0) AS dynamicCost
            FROM inv_dynamic_profit GROUP BY snap_month ORDER BY snap_month
            """)
        List<Map<String, Object>> selectProfitTrend();

        /** 货值供销存月度趋势（跨项目合计） */
        @Select("""
            SELECT flow_month AS month,
                   COALESCE(SUM(sold_value), 0) AS sold,
                   COALESCE(SUM(supply_value), 0) AS supply,
                   COALESCE(SUM(stock_value), 0) AS stock
            FROM inv_value_flow GROUP BY flow_month ORDER BY flow_month
            """)
        List<Map<String, Object>> selectFlowTrend();
    }
}
