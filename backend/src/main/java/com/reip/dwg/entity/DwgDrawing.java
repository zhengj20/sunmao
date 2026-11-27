package com.reip.dwg.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设计图纸审查单（一次上传 + 一次审查）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dwg_drawing")
public class DwgDrawing extends BaseEntity {

    private String drawingCode;
    private Long projectId;
    /** 专业 ARCH建筑/STRUCT结构/ELEC电气/PLUMB给排水/HVAC暖通/INTERIOR精装/LANDSCAPE景观 */
    private String discipline;
    private String title;
    /** 图版/版本，如 A、B、V1.0（避免与 BaseEntity.version 乐观锁字段冲突） */
    private String drawingVersion;
    /** SCHEME方案/CONSTRUCTION施工图/AS_BUILT竣工图 */
    private String stage;
    private String designUnit;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Integer pageCount;
    /** PENDING待审/REVIEWING审查中/PASSED通过/REJECTED驳回 */
    private String status;
    /** RULE规则引擎/LLM大模型 */
    private String aiMode;
    private String aiNote;
    private String reviewerName;
    private String remark;

    // ---------- 列表聚合展示（非表字段，按 drawing_id 聚合填充） ----------
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private Integer totalIssues;
    @TableField(exist = false)
    private Integer openIssues;
    @TableField(exist = false)
    private Integer resolvedIssues;
}
