package com.reip.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.platform.entity.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface PlatformMappers {

    @Mapper interface UserMapper extends BaseMapper<SysUser> {}
    @Mapper interface RoleMapper extends BaseMapper<SysRole> {}
    @Mapper interface ParamMapper extends BaseMapper<SysParam> {}
    @Mapper interface OpLogMapper extends BaseMapper<SysOpLog> {}

    @Mapper
    interface MenuMapper extends BaseMapper<SysMenu> {
        @Select("""
            SELECT DISTINCT m.* FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.visible = 1
            ORDER BY m.sort
            """)
        List<SysMenu> selectByUserId(Long userId);

        @Select("""
            SELECT DISTINCT m.perm_code FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.type = 'B' AND m.perm_code IS NOT NULL
            """)
        List<String> selectPermsByUserId(Long userId);
    }

    @Mapper
    interface UserRoleMapper {
        @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
        List<Long> selectRoleIds(Long userId);

        @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
        List<Long> selectUserIds(Long roleId);

        @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
        void insert(Long userId, Long roleId);

        @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
        void deleteByUserId(Long userId);
    }

    @Mapper
    interface RoleMenuMapper {
        @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
        List<Long> selectMenuIds(Long roleId);

        @Insert("INSERT INTO sys_role_menu(role_id, menu_id) VALUES(#{roleId}, #{menuId})")
        void insert(Long roleId, Long menuId);

        @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
        void deleteByRoleId(Long roleId);
    }
}
