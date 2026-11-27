-- =====================================================
-- V17 菜单种子：P0/P1 新增页面接入侧栏
--   零星采购(proc) / 专家库(proc) / 公告管理(sys) / 流程设计器(workflow)
-- =====================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(104, 3, '零星采购',   'M', '/proc/misc',        'proc/misc/index',        '', 12),
(105, 3, '专家库',     'M', '/proc/expert',      'proc/expert/index',      '', 13),
(106, 9, '公告管理',   'M', '/sys/notice',       'sys/notice/index',       '', 6),
(107, 7, '流程设计器', 'M', '/workflow/designer','workflow/designer/index','', 6);

-- 角色授权：授予主要业务角色（1集团管理员 / 2成本经理 / 3招采经理）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 104), (2, 104), (3, 104),
(1, 105), (2, 105), (3, 105),
(1, 106), (2, 106), (3, 106),
(1, 107), (2, 107), (3, 107);
