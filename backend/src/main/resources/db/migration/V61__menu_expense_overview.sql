-- =====================================================
-- V61 费用管理菜单：新增「费用总览」「费用驾驶舱」
--   总览：KPI + 预算执行 + 月度趋势 + 快捷入口
--   驾驶舱：KPI + 趋势/构成/三费占比/预警（复用 /expense/reports 数据，前端聚合）
--   菜单 id：费用线 131-139 已用，118-125 被销售模块占用，126-129 未被占用
-- =====================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(126, 6, '费用总览',   'M', '/expense/overview', 'expense/overview/index', '', 0),
(127, 6, '费用驾驶舱', 'M', '/expense/cockpit',  'expense/cockpit/index',  '', 1);

-- 角色授权：集团管理员(1) / 成本经理(2) / 财务经理(4)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 126), (1, 127),
(2, 126), (2, 127),
(4, 126), (4, 127);
