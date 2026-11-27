package com.reip.digital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.digital.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数字员工团队模块 Mapper 集合（风格同 AiMappers / MdmMappers）
 */
public interface DigitalMappers {

    @Mapper interface EmployeeMapper extends BaseMapper<DigEmployee> {}

    @Mapper interface SkillMapper extends BaseMapper<DigSkill> {}

    @Mapper interface EmployeeSkillMapper extends BaseMapper<DigEmployeeSkill> {}

    @Mapper interface TaskMapper extends BaseMapper<DigTask> {}

    @Mapper interface TaskRunMapper extends BaseMapper<DigTaskRun> {}

    @Mapper interface WorkflowMapper extends BaseMapper<DigWorkflow> {}

    @Mapper interface WorkflowStepMapper extends BaseMapper<DigWorkflowStep> {
        /** 物理删除某工作流的全部步骤（uk_wf_order 唯一键要求；逻辑删除会残留行导致重建冲突） */
        @org.apache.ibatis.annotations.Delete("DELETE FROM dig_workflow_step WHERE wf_id = #{wfId}")
        int physicalDeleteByWf(@org.apache.ibatis.annotations.Param("wfId") Long wfId);
    }

    @Mapper interface WorkflowRunMapper extends BaseMapper<DigWorkflowRun> {}

    @Mapper interface WfApprovalMapper extends BaseMapper<DigWfApproval> {}

    @Mapper interface ToolMapper extends BaseMapper<DigTool> {}
}
