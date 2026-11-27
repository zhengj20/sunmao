package com.reip.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.workflow.entity.WflCc;
import com.reip.workflow.entity.WflEntrust;
import com.reip.workflow.entity.WflInstance;
import com.reip.workflow.entity.WflTask;
import com.reip.workflow.entity.WflTemplate;
import com.reip.workflow.entity.WflTimeoutLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WorkflowMappers {

    @Mapper interface TemplateMapper extends BaseMapper<WflTemplate> {}
    @Mapper interface TaskMapper extends BaseMapper<WflTask> {}
    @Mapper interface EntrustMapper extends BaseMapper<WflEntrust> {}
    @Mapper interface TimeoutLogMapper extends BaseMapper<WflTimeoutLog> {}
    @Mapper interface CcMapper extends BaseMapper<WflCc> {}

    @Mapper
    interface InstanceMapper extends BaseMapper<WflInstance> {
        /** 流程监控统计：在途数/超时数/本月完成数/平均审批时长分钟 */
        @Select("""
            SELECT
              (SELECT COUNT(*) FROM wfl_instance WHERE deleted = 0 AND status = 'RUNNING') AS inflightCount,
              (SELECT COUNT(*) FROM wfl_task WHERE deleted = 0 AND status = 'PENDING' AND arrived_at < #{deadline}) AS timeoutCount,
              (SELECT COUNT(*) FROM wfl_instance WHERE deleted = 0 AND finished_at IS NOT NULL AND finished_at >= #{monthStart}) AS monthFinishedCount,
              (SELECT IFNULL(AVG(TIMESTAMPDIFF(MINUTE, submitted_at, finished_at)), 0)
                 FROM wfl_instance WHERE deleted = 0 AND finished_at IS NOT NULL) AS avgDurationMinutes
            """)
        Map<String, Object> selectStats(@Param("deadline") LocalDateTime deadline,
                                        @Param("monthStart") LocalDateTime monthStart);
    }

    /** 审批人查找（sys_user / sys_role） */
    @Mapper
    interface UserLookupMapper {
        /** 按角色编码查所有启用用户 */
        @Select("""
            SELECT DISTINCT u.id, u.real_name
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.code = #{roleCode} AND r.status = 1 AND u.status = 1 AND u.deleted = 0
            ORDER BY u.id
            """)
        List<Map<String, Object>> selectUsersByRoleCode(@Param("roleCode") String roleCode);

        /** 按用户ID批量查启用用户 */
        @Select("""
            <script>
            SELECT id, real_name FROM sys_user
            WHERE deleted = 0 AND status = 1 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
        List<Map<String, Object>> selectUsersByIds(@Param("ids") Collection<Long> ids);
    }
}
