package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 组织机构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_org")
public class MdmOrg extends BaseEntity {
    /** 上级组织ID，0=根节点 */
    private Long parentId;
    /** 组织名称 */
    private String name;
    /** 组织编码 */
    private String code;
    /** 组织类型：GROUP集团/REGION区域/CITY城市/PROJECT项目公司/DEPT部门 */
    private String orgType;
    /** 负责人用户ID */
    private Long leaderUserId;
    /** 成本中心映射编码 */
    private String costCenterCode;
    /** 状态：ACTIVE正常/FROZEN冻结 */
    private String status;
    /** 排序号 */
    private Integer sort;

    /** 树形组装用 */
    private transient List<MdmOrg> children;
}
