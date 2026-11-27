package com.reip.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.framework.auth.JwtUtil;
import com.reip.framework.auth.UserContext;
import com.reip.platform.entity.SysMenu;
import com.reip.platform.entity.SysUser;
import com.reip.platform.mapper.PlatformMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlatformMappers.UserMapper userMapper;
    private final PlatformMappers.MenuMapper menuMapper;
    private final PlatformMappers.UserRoleMapper userRoleMapper;
    private final JwtUtil jwtUtil;
    private final SysMgmtService sysMgmtService;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public Map<String, Object> login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user == null) throw new BizException("用户不存在");
        if (user.getStatus() != null && user.getStatus() == 0) throw new BizException("账号已冻结");

        String stored = user.getPassword();
        boolean ok;
        if (stored != null && stored.startsWith("PLAIN:")) {
            ok = stored.substring(6).equals(password);
            if (ok) { // 首次登录升级为 BCrypt
                user.setPassword(ENCODER.encode(password));
            }
        } else {
            ok = stored != null && ENCODER.matches(password, stored);
        }
        if (!ok) throw new BizException("用户名或密码错误");

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("orgId", user.getOrgId());
        data.put("roleIds", userRoleMapper.selectRoleIds(user.getId()));
        return data;
    }

    /** 当前用户菜单树（按 parent_id 组装，组内按 sort 排序） */
    public List<SysMenu> myMenus() {
        Long uid = UserContext.require().getUserId();
        return sysMgmtService.buildTree(menuMapper.selectByUserId(uid));
    }

    /** 当前用户权限码集合 */
    public List<String> myPerms() {
        Long uid = UserContext.require().getUserId();
        return menuMapper.selectPermsByUserId(uid);
    }
}
