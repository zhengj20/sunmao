package com.reip.dwg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.dwg.entity.DwgChecklist;
import com.reip.dwg.entity.DwgDrawing;
import com.reip.dwg.entity.DwgIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

/**
 * 设计审图 Mapper 聚合（模式同 PlanMappers / CostMappers）
 */
public interface DwgMappers {

    @Mapper
    interface DrawingMapper extends BaseMapper<DwgDrawing> {}

    @Mapper
    interface ChecklistMapper extends BaseMapper<DwgChecklist> {}

    @Mapper
    interface IssueMapper extends BaseMapper<DwgIssue> {

        /** 按审查单聚合问题统计（total / open / resolved） */
        @Select("<script>"
                + "SELECT drawing_id AS drawingId, COUNT(*) AS total, "
                + "SUM(CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END) AS open, "
                + "SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved "
                + "FROM dwg_issue WHERE deleted = 0 AND drawing_id IN "
                + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
                + "GROUP BY drawing_id"
                + "</script>")
        List<Map<String, Object>> countsByDrawing(@Param("ids") List<Long> ids);

        /** 某审查单下某来源的问题（AI 预审重跑时清理旧 AI 要点） */
        @Delete("DELETE FROM dwg_issue WHERE drawing_id = #{drawingId} AND source = #{source} AND deleted = 0")
        int deleteByDrawingAndSource(@Param("drawingId") Long drawingId, @Param("source") String source);
    }

    /** MDM 主数据只读：项目名称带出 */
    @Mapper
    interface MdmLookupMapper {
        @Select("SELECT name FROM mdm_project WHERE id = #{id} AND deleted = 0")
        String selectProjectName(@Param("id") Long id);
    }
}
