-- 成本管理(id=4)下新增「变更事项」：签证/设计变更的上游申请，1事项→N变更
--   菜单 id 511（成本模块 51-66 已占用，510+ 空闲）；sort=9 贴近「变更管理」(sort=8)
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(511, 4, '变更事项', 'M', '/cost/change-matter', 'cost/changeMatter/index', '', 9);

-- 角色授权：集团管理员(1) / 成本经理(2)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 511), (2, 511);
