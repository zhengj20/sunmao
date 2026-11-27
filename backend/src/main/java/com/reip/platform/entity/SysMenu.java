package com.reip.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private Long parentId;
    private String name;
    /** D目录 M菜单 B按钮 */
    private String type;
    /** 路由路径 */
    private String path;
    /** 前端组件路径，如 mdm/project/index */
    private String component;
    /** 权限码，如 mdm:supplier:create */
    private String permCode;
    private String icon;
    private Integer sort;
    private Integer visible;

    /** 树形组装用 */
    private transient List<SysMenu> children;
}
