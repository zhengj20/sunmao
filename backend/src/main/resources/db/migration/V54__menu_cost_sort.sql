-- 成本管理(id=4)子菜单顺序优化，按业务主线排列：
-- 总览(驾驶舱/合同总览) → 规划(目标成本/合约规划) → 执行(合同管理/付款计划/付款管理)
-- → 变更(变更管理/补充协议) → 监控(动态成本/无效成本) → 工具(成本指标库/合同模板)
UPDATE sys_menu SET sort = 1  WHERE id = 64; -- 成本驾驶舱
UPDATE sys_menu SET sort = 2  WHERE id = 60; -- 合同总览
UPDATE sys_menu SET sort = 3  WHERE id = 51; -- 目标成本
UPDATE sys_menu SET sort = 4  WHERE id = 52; -- 合约规划
UPDATE sys_menu SET sort = 5  WHERE id = 53; -- 合同管理
UPDATE sys_menu SET sort = 6  WHERE id = 66; -- 付款计划
UPDATE sys_menu SET sort = 7  WHERE id = 55; -- 付款管理
UPDATE sys_menu SET sort = 8  WHERE id = 54; -- 变更管理
UPDATE sys_menu SET sort = 9  WHERE id = 59; -- 补充协议
UPDATE sys_menu SET sort = 10 WHERE id = 56; -- 动态成本
UPDATE sys_menu SET sort = 11 WHERE id = 57; -- 无效成本
UPDATE sys_menu SET sort = 12 WHERE id = 58; -- 成本指标库
UPDATE sys_menu SET sort = 13 WHERE id = 65; -- 合同模板
