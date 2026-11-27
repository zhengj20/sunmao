package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 统一码值字典
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_dict")
public class MdmDict extends BaseEntity {
    /** 字典键，如 project.status */
    private String dictKey;
    /** 显示名 */
    private String label;
    /** 码值 */
    private String value;
    /** 排序号 */
    private Integer sort;
    private String remark;
}
