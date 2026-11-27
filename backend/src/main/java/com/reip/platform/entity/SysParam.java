package com.reip.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_param")
public class SysParam extends BaseEntity {
    private String paramKey;
    private String paramValue;
    private String name;
    private String remark;
}
