-- =====================================================
-- V27 审批中心菜单拆分
--   待办任务 / 已办任务 / 我发起的   → 归入「工作台」
--   流程设计 / 流程监控 / 流程设计器  → 归入「系统管理」
--   删除「审批中心」一级目录
-- 说明：仅调整 sys_menu 的 parent_id/sort/type/visible 与新增一个「总览」子项，
--       页面与路由零改动(/workflow/* 路由保留)。
-- 角色影响：81/82/83 的所有授权角色均已含工作台(1)权限，访问不丢失；
--           84/85/107 归入系统管理后为系统级功能(仅系统管理可见角色可访问)。
-- =====================================================

-- 1) 工作台新增「总览」子项，保留 /workbench 首页入口，并使其成为目录
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort)
VALUES (300, 1, '总览', 'M', '/workbench', 'workbench/index', 'Odometer', 0);
UPDATE sys_menu SET type = 'D' WHERE id = 1;

-- 2) 待办/已办/我发起的 → 挂到工作台(1)
UPDATE sys_menu SET parent_id = 1, sort = 1 WHERE id = 81;  -- 待办任务
UPDATE sys_menu SET parent_id = 1, sort = 2 WHERE id = 82;  -- 已办任务
UPDATE sys_menu SET parent_id = 1, sort = 3 WHERE id = 83;  -- 我发起的

-- 3) 流程设计/监控/设计器 → 挂到系统管理(9)
UPDATE sys_menu SET parent_id = 9, sort = 97 WHERE id = 84;  -- 流程设计
UPDATE sys_menu SET parent_id = 9, sort = 98 WHERE id = 85;  -- 流程监控
UPDATE sys_menu SET parent_id = 9, sort = 99 WHERE id = 107; -- 流程设计器

-- 4) 隐藏审批中心(7)（子项已全部迁出，避免空目录）
UPDATE sys_menu SET visible = 0 WHERE id = 7;
