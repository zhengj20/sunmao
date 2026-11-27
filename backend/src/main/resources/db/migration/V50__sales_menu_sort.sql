-- =====================================================
-- V50 销售管理(id=11)子菜单排序整理，按业务分组：
--   经营分析(驾驶舱/总览/漏斗/顾问) → 营销获客(跟进/渠道/目标) → 房源价格(销控/价格)
--   → 交易执行(认购→签约→回款→退换) → 报表
-- =====================================================
UPDATE sys_menu SET sort = 1  WHERE id = 118;   -- 销售驾驶舱
UPDATE sys_menu SET sort = 2  WHERE id = 119;   -- 销售总览
UPDATE sys_menu SET sort = 3  WHERE id = 124;   -- 蓄客漏斗
UPDATE sys_menu SET sort = 4  WHERE id = 123;   -- 置业顾问业绩
UPDATE sys_menu SET sort = 5  WHERE id = 120;   -- 客户跟进
UPDATE sys_menu SET sort = 6  WHERE id = 122;   -- 渠道管理
UPDATE sys_menu SET sort = 7  WHERE id = 121;   -- 销售目标
UPDATE sys_menu SET sort = 8  WHERE id = 111;   -- 房源销控
UPDATE sys_menu SET sort = 9  WHERE id = 112;   -- 价格折扣
UPDATE sys_menu SET sort = 10 WHERE id = 113;   -- 认购管理
UPDATE sys_menu SET sort = 11 WHERE id = 114;   -- 签约管理
UPDATE sys_menu SET sort = 12 WHERE id = 115;   -- 回款管理
UPDATE sys_menu SET sort = 13 WHERE id = 116;   -- 退换管理
UPDATE sys_menu SET sort = 14 WHERE id = 117;   -- 销售报表
