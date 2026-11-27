package com.reip.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.plan.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 计划模块 Mapper 聚合（模式同 ExpenseMappers / SaleMappers）
 * 含跨模块只读：mdm_project / mdm_org；形象进度 R4 校验查询；报表聚合
 */
public interface PlanMappers {

    @Mapper
    interface TemplateMapper extends BaseMapper<PlanTemplate> {}

    @Mapper
    interface NodeLibMapper extends BaseMapper<PlanNodeLib> {}

    @Mapper
    interface TaskMapper extends BaseMapper<PlanTask> {}

    @Mapper
    interface AdjustMapper extends BaseMapper<PlanAdjust> {}

    @Mapper
    interface ProgressReportMapper extends BaseMapper<PlanProgressReport> {}

    @Mapper
    interface MeetingMapper extends BaseMapper<PlanMeeting> {}

    @Mapper
    interface MeetingTaskMapper extends BaseMapper<MeetingTask> {}

    @Mapper
    interface ImageProgressMapper extends BaseMapper<PlanImageProgress> {

        /** R4 校验：某项目×楼栋×部位 已确认形象进度最高%（无则 0） */
        @Select("SELECT COALESCE(MAX(confirmed_pct),0) FROM plan_image_progress "
                + "WHERE deleted = 0 AND project_id = #{projectId} AND building = #{building} "
                + "AND part_name = #{partName} AND status = 'CONFIRMED'")
        BigDecimal maxConfirmedPct(@Param("projectId") Long projectId,
                                   @Param("building") String building,
                                   @Param("partName") String partName);
    }

    /** MDM 主数据只读查询（编码上下文 / 名称带出） */
    @Mapper
    interface MdmLookupMapper {
        @Select("SELECT project_code FROM mdm_project WHERE id = #{id} AND deleted = 0")
        String selectProjectCode(Long id);

        @Select("SELECT name FROM mdm_org WHERE id = #{id} AND deleted = 0")
        String selectOrgName(Long id);
    }

    /** 计划报表聚合查询 */
    @Mapper
    interface ReportMapper {

        /** 各单位执行排名（红黄绿统计 + 完成率） */
        @Select("<script>"
                + "SELECT t.resp_org_id AS orgId, o.name AS orgName, "
                + "COUNT(*) AS total, "
                + "SUM(CASE WHEN t.alert_status='GREEN' THEN 1 ELSE 0 END) AS green, "
                + "SUM(CASE WHEN t.alert_status='YELLOW' THEN 1 ELSE 0 END) AS yellow, "
                + "SUM(CASE WHEN t.alert_status='RED' THEN 1 ELSE 0 END) AS red, "
                + "SUM(CASE WHEN t.status='COMPLETED' THEN 1 ELSE 0 END) AS completed "
                + "FROM plan_task t LEFT JOIN mdm_org o ON o.id = t.resp_org_id AND o.deleted = 0 "
                + "WHERE t.deleted = 0 "
                + "<if test='projectId != null'> AND t.project_id = #{projectId} </if> "
                + "GROUP BY t.resp_org_id, o.name ORDER BY red DESC, yellow DESC, orgName"
                + "</script>")
        List<Map<String, Object>> ranking(@Param("projectId") Long projectId);
    }
}
