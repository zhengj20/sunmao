package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购计划（月度汇总，由已审批需求合并而成）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_plan")
public class ProcPlan extends BaseEntity {

    /** 计划编号（JH-yyyy-MM-时间戳） */
    private String planCode;
    /** 业务项目 */
    private Long projectId;
    /** 计划月份 yyyy-MM */
    private String planMonth;
    /** 计划名称 */
    private String name;
    /** DRAFT/CONFIRMED */
    private String status;
    private String remark;
}
