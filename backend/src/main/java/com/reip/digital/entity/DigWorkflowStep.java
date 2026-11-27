package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_workflow_step")
public class DigWorkflowStep extends BaseEntity {
    private Long wfId;
    private Integer stepOrder;
    private Long taskId;
    private String name;
    private String conditionExpr;
    private String onFail;
    private Integer timeoutSec;
    /** 接力上一环节产物注入下游 Prompt 1=是 0=否 */
    private Integer usePrev;
    /** 需人工审批（执行到此暂停，批准/驳回）1=是 0=否 */
    private Integer requiresApproval;
}
