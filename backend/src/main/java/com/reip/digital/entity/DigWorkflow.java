package com.reip.digital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dig_workflow")
public class DigWorkflow extends BaseEntity {
    private String wfCode;
    private String name;
    private String description;
    /** CRON(6段含秒)，空=仅手动触发 */
    private String cronExpr;
    /** 定时工作流下次执行时间 */
    private java.time.LocalDateTime nextRunAt;
    /** 绑定知识库文档ID（逗号分隔，作用于全部环节） */
    private String kbDocIds;
    private String status;
}
