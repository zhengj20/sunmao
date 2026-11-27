-- =====================================================
-- V34 招采管理子菜单重排序：「招采驾驶舱」置顶
--   → 左侧菜单第一项 + 点击顶部「招采管理」默认进入驾驶舱
-- =====================================================
UPDATE sys_menu SET sort = 0  WHERE id = 109;  -- 招采驾驶舱
UPDATE sys_menu SET sort = 1  WHERE id = 108;  -- 招采过程总览
UPDATE sys_menu SET sort = 2  WHERE id = 31;   -- 需求
UPDATE sys_menu SET sort = 3  WHERE id = 32;   -- 采购计划
UPDATE sys_menu SET sort = 4  WHERE id = 33;   -- 招采立项
UPDATE sys_menu SET sort = 5  WHERE id = 34;   -- 入围
UPDATE sys_menu SET sort = 6  WHERE id = 35;   -- 发标
UPDATE sys_menu SET sort = 7  WHERE id = 36;   -- 回标
UPDATE sys_menu SET sort = 8  WHERE id = 37;   -- 评标
UPDATE sys_menu SET sort = 9  WHERE id = 38;   -- 定标
UPDATE sys_menu SET sort = 10 WHERE id = 39;   -- 直接委托
UPDATE sys_menu SET sort = 11 WHERE id = 40;   -- 战略集采
UPDATE sys_menu SET sort = 12 WHERE id = 41;   -- 全流程台账
UPDATE sys_menu SET sort = 13 WHERE id = 104;  -- 其他
UPDATE sys_menu SET sort = 14 WHERE id = 105;  -- 专家库
