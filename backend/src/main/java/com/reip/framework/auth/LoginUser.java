package com.reip.framework.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户（ThreadLocal 上下文）
 */
@Data
@AllArgsConstructor
public class LoginUser {
    private Long userId;
    private String username;
    private String realName;
    private Long orgId;
}
