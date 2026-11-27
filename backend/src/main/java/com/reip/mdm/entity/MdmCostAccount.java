package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 成本科目树（集团模板 + 项目克隆）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_cost_account")
public class MdmCostAccount extends BaseEntity {
    /** 所属项目，NULL=集团模板 */
    private Long projectId;
    /** 上级科目ID，0=根节点 */
    private Long parentId;
    /** 科目编码 */
    private String code;
    /** 科目名称 */
    private String name;
    /** 级次1-6 */
    private Integer level;
    /** 1末级 0非末级 */
    private Integer leaf;
    /** 控制方式：刚性/预警/不限 */
    private String controlMode;
    /** 分摊规则 */
    private String allocateRule;
    /** 财务核算科目映射 */
    private String financeAccount;
    /** 排序号 */
    private Integer sort;

    /** 树形组装用 */
    private transient List<MdmCostAccount> children;
}
