package com.reip.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String password;
    private String realName;
    private String mobile;
    private String email;
    private Long orgId;
    /** 职务级别（费控标准用） */
    private String jobLevel;
    /** 1启用 0冻结 */
    private Integer status;
    private LocalDateTime lastLoginAt;
}
