package com.reip.plan.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 计划标准模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plan_template")
public class PlanTemplate extends BaseEntity {

    private String name;
    /** 业态(住宅/商业/车位/综合体) */
    private String bizType;
    /** 规模(大型/中型/小型) */
    private String scale;
    /** 模板版本(对应列 tpl_version) */
    private String tplVersion;
    /** ACTIVE/ARCHIVED */
    private String status;

    /** 展示用：节点库（非持久化） */
    private transient List<PlanNodeLib> nodes;
}
