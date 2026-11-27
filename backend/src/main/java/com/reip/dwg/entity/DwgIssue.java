package com.reip.dwg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 图纸审查问题（AI 生成 + 人工提出，逐条解决）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dwg_issue")
public class DwgIssue extends BaseEntity {

    private Long drawingId;
    private Integer seq;
    /** 图面标注点 X（0~100，占图纸宽度百分比）；AI 预审问题为空 */
    private Double markerX;
    /** 图面标注点 Y（0~100，占图纸高度百分比） */
    private Double markerY;
    /** AI系统预审 / MANUAL人工提出 */
    private String source;
    /** HIGH严重/MEDIUM一般/LOW建议 */
    private String level;
    private String category;
    /** 图号/部位/轴号 */
    private String location;
    private String description;
    private String suggestion;
    /** OPEN待解决/RESOLVED已解决/REJECTED无需处理 */
    private String status;
    private String assigneeName;
    private String resolveNote;
    private Long resolverId;
    private LocalDate resolveDate;
}
