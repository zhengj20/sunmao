package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 目标成本版本
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_target_version")
public class CostTargetVersion extends BaseEntity {

    /** 版本编号（MB 规则生成） */
    private String versionCode;
    /** 项目 */
    private Long projectId;
    /** 阶段：LAND拿地版/SCHEME方案版/CONSTRUCTION施工图版/BASE基准版 */
    private String stage;
    /** 目标总成本（明细金额合计） */
    private BigDecimal totalAmount;
    /** 建筑面积（计算单方用） */
    private BigDecimal buildArea;
    /** 状态：DRAFT/APPROVING/APPROVED/LOCKED */
    private String status;
    private String remark;

    /** 明细列表（查询返回，非表字段） */
    @TableField(exist = false)
    private List<CostTargetItem> items;
}
