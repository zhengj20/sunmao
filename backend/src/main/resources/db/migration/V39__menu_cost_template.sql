-- 成本管理(id=4)下新增「合同模板」：上传/预览 .docx 模板、按模板生成合同
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(65, 4, '合同模板', 'M', '/cost/template', 'cost/template/index', '', 11);

-- 角色授权：集团管理员(1) / 成本经理(2)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 65), (2, 65);
