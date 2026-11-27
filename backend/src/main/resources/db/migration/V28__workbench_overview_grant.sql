-- =====================================================
-- V28 补齐「总览」(id=300) 的角色授权
-- V27 新增工作台「总览」子项时漏了 sys_role_menu 授权，
-- 而 admin 等角色的授权是 V1 快照写入的(不含新 id)，导致看不到。
-- 工作台为通用入口，授予全部角色(防重)。
-- =====================================================
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 300 FROM sys_role r
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 300
);
