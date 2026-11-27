package com.reip.dwg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图纸审查要点库（AI 预审规则源，可配置）
 * discipline=ALL 表示通用要点；stage 为空=所有阶段，否则仅该阶段生效
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dwg_checklist")
public class DwgChecklist extends BaseEntity {

    /** ARCH/STRUCT/ELEC/PLUMB/HVAC/INTERIOR/LANDSCAPE/ALL通用 */
    private String discipline;
    /** 阶段为空=所有阶段；否则仅该阶段生效 */
    private String stage;
    /** HIGH严重/MEDIUM一般/LOW建议 */
    private String level;
    private String category;
    private String description;
    private String suggestion;
    /** 1启用 0停用 */
    private Integer enabled;
    private Integer sort;
}
