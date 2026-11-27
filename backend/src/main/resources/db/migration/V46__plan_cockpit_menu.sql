-- =====================================================
-- V46 项目计划模块下新增「项目驾驶舱」菜单链接
--   复用既有菜单(203)的页面组件 cockpit/project/index（同页面、第二个入口）
--   新菜单 id=150，path=/plan/cockpit，置于「计划总览」之后(sort=1)，
--   原 141-149 子菜单 sort 顺延 +1
-- =====================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(150, 14, '项目驾驶舱', 'M', '/plan/cockpit', 'cockpit/project/index', '', 1);

-- 原计划模块子菜单(141-149)排序顺延，为新菜单腾出 sort=1
UPDATE sys_menu SET sort = sort + 1 WHERE id IN (141, 142, 143, 144, 145, 146, 147, 148, 149);

-- 角色授权：集团管理员(1) / 成本经理(2) / 项目运营(7)，与「计划总览」(140)保持一致
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 150), (2, 150), (7, 150);
