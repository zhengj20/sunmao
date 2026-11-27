package com.reip.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.UserContext;
import com.reip.platform.entity.SysMenu;
import com.reip.platform.entity.SysOpLog;
import com.reip.platform.entity.SysRole;
import com.reip.platform.entity.SysUser;
import com.reip.platform.mapper.PlatformMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMgmtService {

    private final PlatformMappers.UserMapper userMapper;
    private final PlatformMappers.RoleMapper roleMapper;
    private final PlatformMappers.MenuMapper menuMapper;
    private final PlatformMappers.ParamMapper paramMapper;
    private final PlatformMappers.OpLogMapper opLogMapper;
    private final PlatformMappers.UserRoleMapper userRoleMapper;
    private final PlatformMappers.RoleMenuMapper roleMenuMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ================= 用户 =================
    public PageResult<SysUser> userPage(long current, long size, String kw, Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(SysUser::getUsername, kw)
                .or().like(SysUser::getRealName, kw).or().like(SysUser::getMobile, kw));
        if (status != null) qw.eq(SysUser::getStatus, status);
        qw.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(u -> u.setPassword(null));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Long createUser(SysUser user, List<Long> roleIds) {
        Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (cnt > 0) throw new BizException("登录名已存在：" + user.getUsername());
        user.setId(null);
        user.setPassword(ENCODER.encode(user.getPassword() == null || user.getPassword().isBlank()
                ? "Reip@123" : user.getPassword()));
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        userMapper.insert(user);
        if (roleIds != null) roleIds.forEach(rid -> userRoleMapper.insert(user.getId(), rid));
        return user.getId();
    }

    @Transactional
    public void updateUser(SysUser user, List<Long> roleIds) {
        if (user.getId() == null) throw new BizException("缺少用户ID");
        user.setPassword(null); // 不走此接口改密码
        int updated = userMapper.updateById(user);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        userRoleMapper.deleteByUserId(user.getId());
        if (roleIds != null) roleIds.forEach(rid -> userRoleMapper.insert(user.getId(), rid));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        SysUser up = new SysUser();
        up.setId(userId);
        up.setVersion(u.getVersion());
        up.setPassword(ENCODER.encode(newPassword));
        userMapper.updateById(up);
    }

    @Transactional
    public void toggleUserStatus(Long userId, Integer status) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        SysUser up = new SysUser();
        up.setId(userId);
        up.setVersion(u.getVersion());
        up.setStatus(status);
        userMapper.updateById(up);
    }

    public List<Long> userRoleIds(Long userId) {
        return userRoleMapper.selectRoleIds(userId);
    }

    // ================= 角色 =================
    public List<SysRole> roleList() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getId));
    }

    @Transactional
    public Long saveRole(SysRole role, List<Long> menuIds) {
        if (role.getId() == null) {
            Long cnt = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, role.getCode()));
            if (cnt > 0) throw new BizException("角色编码已存在");
            roleMapper.insert(role);
        } else {
            int updated = roleMapper.updateById(role);
            if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
            roleMenuMapper.deleteByRoleId(role.getId());
        }
        if (menuIds != null) menuIds.forEach(mid -> roleMenuMapper.insert(role.getId(), mid));
        return role.getId();
    }

    public List<Long> roleMenuIds(Long roleId) {
        return roleMenuMapper.selectMenuIds(roleId);
    }

    // ================= 菜单 =================
    public List<SysMenu> menuTree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getVisible, 1).orderByAsc(SysMenu::getSort));
        return buildTree(all);
    }

    public List<SysMenu> buildTree(List<SysMenu> all) {
        Map<Long, List<SysMenu>> byParent = all.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(SysMenu::getParentId));
        List<SysMenu> roots = all.stream().filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .collect(Collectors.toList());
        roots.forEach(r -> attach(r, byParent));
        return roots;
    }

    private void attach(SysMenu node, Map<Long, List<SysMenu>> byParent) {
        List<SysMenu> children = byParent.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            children.forEach(c -> attach(c, byParent));
        }
    }

    @Transactional
    public Long saveMenu(SysMenu menu) {
        if (menu.getId() == null) menuMapper.insert(menu);
        else {
            int updated = menuMapper.updateById(menu);
            if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }
        return menu.getId();
    }

    @Transactional
    public void deleteMenu(Long id) {
        Long cnt = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (cnt > 0) throw new BizException("存在子菜单，请先删除子节点");
        menuMapper.deleteById(id);
    }

    // ================= 参数 / 日志 =================
    public List<Map<String, String>> paramList() {
        return paramMapper.selectList(null).stream().map(p -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", p.getParamKey());
            m.put("value", p.getParamValue());
            m.put("name", p.getName());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void saveParam(String key, String value) {
        var one = paramMapper.selectOne(new LambdaQueryWrapper<com.reip.platform.entity.SysParam>()
                .eq(com.reip.platform.entity.SysParam::getParamKey, key));
        if (one == null) throw new BizException("参数不存在（演示版仅允许修改预置参数）");
        one.setParamValue(value);
        paramMapper.updateById(one);
    }

    public PageResult<SysOpLog> opLogPage(long current, long size, String module, Long userId) {
        LambdaQueryWrapper<SysOpLog> qw = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) qw.eq(SysOpLog::getModule, module);
        if (userId != null) qw.eq(SysOpLog::getUserId, userId);
        qw.orderByDesc(SysOpLog::getId);
        Page<SysOpLog> p = opLogMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public void logOp(String module, String action, String bizId, String detail) {
        try {
            SysOpLog log = new SysOpLog();
            var u = UserContext.get();
            log.setUserId(u == null ? null : u.getUserId());
            log.setUsername(u == null ? "system" : u.getUsername());
            log.setModule(module);
            log.setAction(action);
            log.setBizId(bizId);
            log.setDetail(detail);
            log.setCreatedAt(LocalDateTime.now());
            opLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }
}
