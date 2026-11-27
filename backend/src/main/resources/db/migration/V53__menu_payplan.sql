-- 成本管理(id=4)下新增「付款计划」：选择合同后在下方子表维护该合同的付款计划（资金计划成本线数据来源）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(66, 4, '付款计划', 'M', '/cost/payplan', 'cost/payplan/index', '', 12);

-- 角色授权：集团管理员(1) / 成本经理(2)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 66), (2, 66);
