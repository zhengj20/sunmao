package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评标专家库（抽取回避：ACTIVE 且 avoid_orgs 不含回避关键词）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_expert")
public class ProcExpert extends BaseEntity {

    /** 专家姓名 */
    private String name;
    /** 联系电话 */
    private String phone;
    /** 专业领域 */
    private String specialty;
    /** 回避单位（逗号分隔，抽取时排除含关键词的专家） */
    private String avoidOrgs;
    /** ACTIVE/INACTIVE */
    private String status;
}
