-- =====================================================
-- V37 成本管理菜单：新增「成本驾驶舱」（成本 KPI + 三算对比 + 构成/趋势 + 预警）
--   数据复用动态成本接口（/cost/dynamic、three-way-compare、warning-center），前端聚合，无后端改动。
--   菜单 id：成本模块 51-59，60=合同总览，61-63=投资，64 未被占用。
-- =====================================================

-- 合同总览(id=60)顺延至第 2，成本驾驶舱置于成本管理首位
UPDATE sys_menu SET sort = 1 WHERE id = 60;

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(64, 4, '成本驾驶舱', 'M', '/cost/cockpit', 'cost/cockpit/index', '', 0);

-- 角色授权：集团管理员(1) / 成本经理(2)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 64), (2, 64);
