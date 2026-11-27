package com.reip.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板（多版本，在途走旧版）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wfl_template")
public class WflTemplate extends BaseEntity {
    private String templateCode;
    private String name;
    /** 绑定的单据类型，如 COST_CONTRACT */
    private String bizType;
    /** 业务版本（在途走旧版） */
    private Integer tplVersion;
    /** 节点定义 JSON */
    private String definition;
    /** ACTIVE/ARCHIVED */
    private String status;
    private String remark;
}
