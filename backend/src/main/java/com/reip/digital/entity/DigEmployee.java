package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数字员工（角色）：招采/成本/财务/营销/自定义，可增删
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_employee")
public class DigEmployee extends BaseEntity {

    private String name;
    /** PROC/COST/FIN/SAL/CUSTOM */
    private String roleCode;
    /** 形象主题 proc/cost/fin/sal/custom */
    private String avatar;
    /** 人设设定（系统提示词前缀） */
    private String persona;
    /** 绑定 ai_model_config.id；空=系统默认激活模型 */
    private Long modelConfigId;
    /** 绑定知识库文档ID（逗号分隔，执行任务时注入提示词） */
    private String kbDocIds;
    /** ACTIVE在岗/INACTIVE休假 */
    private String status;
    private String remark;
}
