package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编码规则
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_code_rule")
public class MdmCodeRule extends BaseEntity {
    /** 规则编码，如 PRJ/HT */
    private String ruleCode;
    /** 规则名称 */
    private String name;
    /** 编码模式，如 HT-{projectCode}-{YYYY}-{seq:4} */
    private String pattern;
    /** 示例编码 */
    private String example;
    private String remark;
}
