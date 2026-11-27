-- V43 调整顶部导航顺序：数字员工移到工作台后面
UPDATE sys_menu SET sort = 10  WHERE id = 1;    -- 工作台
UPDATE sys_menu SET sort = 20  WHERE id = 500;  -- 数字员工
UPDATE sys_menu SET sort = 30  WHERE id = 8;    -- 经营驾驶舱
UPDATE sys_menu SET sort = 40  WHERE id = 5;    -- 投资管理
UPDATE sys_menu SET sort = 50  WHERE id = 3;    -- 招采管理
UPDATE sys_menu SET sort = 60  WHERE id = 4;    -- 成本管理
UPDATE sys_menu SET sort = 70  WHERE id = 14;   -- 项目计划
UPDATE sys_menu SET sort = 80  WHERE id = 11;   -- 营销管理
UPDATE sys_menu SET sort = 90  WHERE id = 6;    -- 资金费用
UPDATE sys_menu SET sort = 100 WHERE id = 2;    -- 主数据
UPDATE sys_menu SET sort = 110 WHERE id = 9;    -- 系统管理
UPDATE sys_menu SET sort = 120 WHERE id = 10;   -- 集成平台
