-- =====================================================
-- V30 成本管理菜单：补充协议
-- =====================================================

-- 补充协议菜单（在成本管理目录下，parent_id=4）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(59, 4, '补充协议', 'M', '/cost/supplement', 'cost/supplement/index', '', 9);

-- 角色授权：集团管理员(1) 和 成本经理(2)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 59), (2, 59);
