-- =====================================================
-- V60 资金费用模块优化（一）：菜单重排
--   1. 一级目录「资金费用」更名「费用管理」（聚焦费用域）
--   2. 资金计划(71)/收付执行(72)/融资台账(73) 移入「全面预算」(301)：
--      预算管控链 年度预算→月度预算→资金计划→收付执行，融资归资金预算域
--   3. 仅调整 name/parent_id/sort，菜单 id 与 path/component 不变，
--      前端路由零改动；sys_role_menu 按 menu_id 授权，无需变更
-- =====================================================

UPDATE sys_menu SET name = '费用管理' WHERE id = 6;

-- 资金三项挂入全面预算，sort 接在 年度预算(1)/月度预算(2)/预算执行(3) 之后
UPDATE sys_menu SET parent_id = 301, sort = 4 WHERE id = 71;  -- 资金计划
UPDATE sys_menu SET parent_id = 301, sort = 5 WHERE id = 72;  -- 收付执行
UPDATE sys_menu SET parent_id = 301, sort = 6 WHERE id = 73;  -- 融资台账
