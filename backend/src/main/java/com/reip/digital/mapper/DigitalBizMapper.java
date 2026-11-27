package com.reip.digital.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数字员工跨域只读取数（仅 SELECT，不写业务表；风格同 AiBizMapper）。
 * 条件触发指标 pendingAward：待审批定标数量。
 */
@Mapper
public interface DigitalBizMapper {

    /** 待审批定标数量（proc_award.status = APPROVING） */
    @Select("SELECT COUNT(*) FROM proc_award WHERE deleted = 0 AND status = 'APPROVING'")
    long countPendingAward();

    /** 在办招采项目数（DRAFT/CLOSED 之外） */
    @Select("SELECT COUNT(*) FROM proc_project WHERE deleted = 0 AND status NOT IN ('DRAFT','CLOSED')")
    long countOpenProcProject();

    /** 已完成执行平均耗时（毫秒） */
    @Select("SELECT COALESCE(AVG(duration_ms), 0) FROM dig_task_run WHERE deleted = 0 AND duration_ms IS NOT NULL")
    long avgDurationMs();

    /** 累计 Token 消耗（输入+输出） */
    @Select("SELECT COALESCE(SUM(token_in), 0) + COALESCE(SUM(token_out), 0) FROM dig_task_run WHERE deleted = 0")
    long totalTokens();

    /** 近 7 天执行趋势（按天聚合 DONE/FAILED） */
    @Select("SELECT DATE(started_at) AS d, " +
            "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS doneCnt, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCnt " +
            "FROM dig_task_run WHERE deleted = 0 " +
            "AND started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(started_at) ORDER BY d")
    List<Map<String, Object>> selectRunTrend7d();
}
