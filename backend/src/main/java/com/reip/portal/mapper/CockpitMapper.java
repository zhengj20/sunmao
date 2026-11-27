package com.reip.portal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱聚合查询（跨域表只读）
 */
@Mapper
public interface CockpitMapper {

    /** 当前货值合计（供货货值） */
    @Select("SELECT COALESCE(SUM(total_value), 0) FROM inv_value WHERE deleted = 0")
    BigDecimal sumSaleValue();

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

    /** 总货值：每项目最新锁定测算版本 sale_value 之和（拿地基线） */
    @Select("""
        SELECT COALESCE(SUM(v.sale_value), 0)
        FROM inv_calc_version v
        JOIN (SELECT project_id, MAX(id) AS mid FROM inv_calc_version WHERE locked = 1 AND deleted = 0 GROUP BY project_id) t
          ON t.mid = v.id
        """)
    BigDecimal sumLockedSaleValue();

    /** 项目级：最新动态成本快照 + 最新动态利润 + 项目信息 */
    @Select("""
        SELECT p.id, p.name, p.status,
               ds.target_total AS targetTotal, ds.dynamic_total AS dynamicCost,
               dp.dynamic_profit AS dynamicProfit
        FROM mdm_project p
        LEFT JOIN cost_dynamic_snapshot ds ON ds.id = (
            SELECT id FROM cost_dynamic_snapshot WHERE project_id = p.id ORDER BY snap_month DESC LIMIT 1)
        LEFT JOIN inv_dynamic_profit dp ON dp.id = (
            SELECT id FROM inv_dynamic_profit WHERE project_id = p.id ORDER BY snap_month DESC LIMIT 1)
        WHERE p.deleted = 0
        ORDER BY p.id
        """)
    List<Map<String, Object>> selectProjects();

    /** 近6个月动态利润月度序列 */
    @Select("""
        SELECT snap_month AS month, COALESCE(SUM(dynamic_profit), 0) AS dynamicProfit
        FROM inv_dynamic_profit WHERE snap_month >= #{startMonth}
        GROUP BY snap_month ORDER BY snap_month
        """)
    List<Map<String, Object>> selectProfitTrend(String startMonth);

    /** 近6个月动态成本月度序列 */
    @Select("""
        SELECT snap_month AS month, COALESCE(SUM(dynamic_total), 0) AS dynamicCost
        FROM cost_dynamic_snapshot WHERE snap_month >= #{startMonth}
        GROUP BY snap_month ORDER BY snap_month
        """)
    List<Map<String, Object>> selectCostTrend(String startMonth);
}
