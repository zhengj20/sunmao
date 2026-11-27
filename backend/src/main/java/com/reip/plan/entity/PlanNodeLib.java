package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板节点库（WBS 标准节点）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_node_lib")
public class PlanNodeLib extends BaseEntity {

    private Long templateId;
    private String nodeName;
    /** 里程碑/一级/二级/三级 */
    private String level;
    /** 基准工期(工天) */
    private Integer baseDuration;
    /** 责任条线(设计/报建/工程/营销/交付) */
    private String respLine;
    /** 预警规则文本 */
    private String alertRule;
    private Integer sortNo;
}
