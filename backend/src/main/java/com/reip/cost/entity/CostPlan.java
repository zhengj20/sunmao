package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 合约规划
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_plan")
public class CostPlan extends BaseEntity {

    /** 规划编号（HYGH 规则生成） */
    private String planCode;
    /** 项目 */
    private Long projectId;
    /** 依据的目标成本版本 */
    private Long targetVersionId;
    private String name;
    /** 状态：DRAFT/APPROVING/APPROVED */
    private String status;
    private String remark;

    /** 明细列表（查询返回，非表字段） */
    @TableField(exist = false)
    private List<CostPlanItem> items;
}
