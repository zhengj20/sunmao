-- =====================================================
-- V44 计划总览菜单 + 项目运营驾驶舱授权
--   1) 项目计划(id=14)下新增「计划总览」(id=140, sort=0 置于目录首位)：
--      主项计划地铁图 / 专项计划进度 / 施工进度矩阵 / 指标统计与预警
--      数据全部来自 plan_task / plan_image_progress 现有表，无新表
--   2) 项目运营(7) 补授驾驶舱子菜单 200-205：
--      V22 只授了目录(8)，V23 未含角色 7，导致侧栏「驾驶舱」打开后无子菜单
-- =====================================================

-- 计划总览（140 未占用；141-149 为既有子菜单）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(140, 14, '计划总览', 'M', '/plan/overview', 'plan/overview/index', '', 0);

-- 角色授权：集团管理员(1) / 成本经理(2) / 项目运营(7)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 140), (2, 140), (7, 140);

-- 项目运营(7) 补授驾驶舱子菜单（INSERT IGNORE 幂等）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 7, id FROM sys_menu WHERE id IN (200, 201, 202, 203, 204, 205);
