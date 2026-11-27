-- =====================================================
-- V33 招采管理菜单：新增「招采驾驶舱」（指标总览 + 统计分析 + 预警）
-- =====================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(109, 3, '招采驾驶舱', 'M', '/proc/cockpit', 'proc/cockpit/index', '', 1);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 109), (2, 109), (3, 109);
