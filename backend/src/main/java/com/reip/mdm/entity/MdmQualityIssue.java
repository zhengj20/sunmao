package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 主数据数据质量巡检问题
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_quality_issue")
public class MdmQualityIssue extends BaseEntity {

    /** 实体类型：SUPPLIER供应商 / CUSTOMER客户 / PROJECT项目 */
    private String entityType;

    /** 主数据实体ID */
    private Long entityId;

    /** 问题类型：MISSING_VALUE缺失 / DUPLICATE重复 */
    private String issueType;

    /** 问题描述 */
    private String detail;

    /** 严重度：WARN（默认） */
    private String severity;

    /** 状态：OPEN待处理 / FIXED已修复 / IGNORED已忽略 */
    private String status;

    /** 扫描时间 */
    private LocalDateTime scannedAt;
}
