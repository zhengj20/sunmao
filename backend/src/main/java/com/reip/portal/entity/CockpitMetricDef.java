package com.reip.portal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 指标字典：口径公式 + 来源表 + 负责人 + 维度 + 生效日期（口径变更留痕）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("metric_def")
public class CockpitMetricDef extends BaseEntity {

    /** 指标编码（业务唯一键） */
    private String metricCode;
    /** 指标名称 */
    private String metricName;
    /** 口径公式文本 */
    private String formula;
    /** 来源表（主数据源） */
    private String sourceTable;
    /** 维度：集团/区域/项目/月度 */
    private String dimension;
    /** 负责人 */
    private String owner;
    /** ACTIVE当前/HISTORY旧版本 */
    private String status;
    /** 口径生效日期 */
    private LocalDate effectiveDate;

    /** 展示用：版本序号（非表字段，detail 装配） */
    private transient Integer versionNo;
}
