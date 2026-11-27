package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 分类树（合同分类/采购品类/变更分类/费用科目）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_category")
public class MdmCategory extends BaseEntity {
    /** 分类类型：CONTRACT合同/PURCHASE采购品类/CHANGE变更/EXPENSE费用 */
    private String categoryType;
    /** 上级分类ID，0=根节点 */
    private Long parentId;
    /** 分类编码 */
    private String code;
    /** 分类名称 */
    private String name;
    private String remark;
    /** 排序号 */
    private Integer sort;

    /** 树形组装用 */
    private transient List<MdmCategory> children;
}
