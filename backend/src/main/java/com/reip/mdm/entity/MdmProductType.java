package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 业态定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_product_type")
public class MdmProductType extends BaseEntity {
    /** 所属项目 */
    private Long projectId;
    /** 业态名称 */
    private String name;
    /** 分类：住宅/商业/车位/配套 */
    private String category;
    /** 1可售 0不可售 */
    private Integer saleable;
    /** 计容面积㎡ */
    private BigDecimal farArea;
    /** 可售面积㎡ */
    private BigDecimal saleableArea;
    /** 排序号 */
    private Integer sort;
}
