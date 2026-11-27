package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具：内置(BUILTIN)/自定义HTTP，技能可绑定，执行时由执行器调用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_tool")
public class DigTool extends BaseEntity {

    private String toolCode;
    private String name;
    private String description;
    /** BUILTIN / HTTP */
    private String type;
    private String url;
    /** GET / POST */
    private String method;
    private String headers;
    private String body;
    private String status;
}
