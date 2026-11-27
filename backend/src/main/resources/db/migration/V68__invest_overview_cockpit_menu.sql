-- =====================================================
-- V68 投资管理菜单：新增「投资驾驶舱」「投资总览」（参考明源投资管理：组合 KPI + 基线 vs 动态 + 预警）
--   后端新增 /api/invest/cockpit 与 /api/invest/overview（InvestOverviewService 只读聚合，无新表）
--   菜单 id：61-63=投资测算/货值台账/动态利润，64-66=成本模块，67/68 未被占用
-- =====================================================

-- 原投资子菜单(61-63)排序顺延，为新菜单腾出 sort=0/1
UPDATE sys_menu SET sort = sort + 1 WHERE id IN (61, 62, 63);

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(67, 5, '投资驾驶舱', 'M', '/invest/cockpit',  'invest/cockpit/index',  '', 0),
(68, 5, '投资总览',   'M', '/invest/overview', 'invest/overview/index', '', 1);

-- 角色授权：集团管理员(1) / 成本经理(2) / 财务经理(4)，与既有投资菜单(61-63)授权范围一致
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 67), (1, 68),
(2, 67), (2, 68),
(4, 67), (4, 68);
