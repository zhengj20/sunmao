package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能：内置(BUILTIN)/市场(MARKET)/自定义(CUSTOM)，全部可编辑配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_skill")
public class DigSkill extends BaseEntity {

    private String skillCode;
    private String name;
    /** BUILTIN/MARKET/CUSTOM */
    private String category;
    /** 取数域 PROC/COST/FIN/SAL/NONE(通用不取数) */
    private String dataSource;
    private String icon;
    private String description;
    /** 提示词模板，支持 {{data}} {{taskName}} 占位符 */
    private String promptTemplate;
    private String source;
    private Integer installCount;
    /** 是否检索知识库 1=是 0=否 */
    private Integer kbSearch;
    /** 绑定的工具ID（逗号分隔） */
    private String toolIds;
    /** 工具参数 JSON，如 {"calc":"(1+2)*3"} */
    private String toolArgs;
    private String status;
}
