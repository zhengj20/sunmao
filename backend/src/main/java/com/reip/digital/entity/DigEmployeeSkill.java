package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工-技能关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_employee_skill")
public class DigEmployeeSkill extends BaseEntity {

    private Long employeeId;
    private Long skillId;
    private Integer sort;
}
