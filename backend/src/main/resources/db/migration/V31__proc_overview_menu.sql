-- =====================================================
-- V31 招采管理菜单：新增「招采过程总览」（地铁图看板，入口置首）
-- =====================================================

-- 招采管理(id=3)下新增菜单，sort=0 置于该目录首位作为总览入口
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(108, 3, '招采过程总览', 'M', '/proc/overview', 'proc/overview/index', '', 0);

-- 角色授权：集团管理员(1) / 成本经理(2) / 招采经理(3)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 108), (2, 108), (3, 108);
