-- =====================================================
-- V51 将「楼栋房源」(210) 从 主数据(2) 移入 销售管理(11)
--   原因：楼栋房源是房源/销控的核心，应与销售流程在一起；
--   且销售经理角色默认无 主数据 权限，移到 销售管理 下即可直接看到。
--   位置：放在 房源销控 之前（先维护房源 → 再销控/定价 → 交易）。
-- =====================================================

UPDATE sys_menu SET parent_id = 11, sort = 8 WHERE id = 210;   -- 楼栋房源

-- 重新整理 销售管理(11) 子菜单顺序
UPDATE sys_menu SET sort = 1  WHERE id = 118;   -- 销售驾驶舱
UPDATE sys_menu SET sort = 2  WHERE id = 119;   -- 销售总览
UPDATE sys_menu SET sort = 3  WHERE id = 124;   -- 蓄客漏斗
UPDATE sys_menu SET sort = 4  WHERE id = 123;   -- 置业顾问业绩
UPDATE sys_menu SET sort = 5  WHERE id = 120;   -- 客户跟进
UPDATE sys_menu SET sort = 6  WHERE id = 122;   -- 渠道管理
UPDATE sys_menu SET sort = 7  WHERE id = 121;   -- 销售目标
UPDATE sys_menu SET sort = 9  WHERE id = 111;   -- 房源销控
UPDATE sys_menu SET sort = 10 WHERE id = 112;   -- 价格折扣
UPDATE sys_menu SET sort = 11 WHERE id = 113;   -- 认购管理
UPDATE sys_menu SET sort = 12 WHERE id = 114;   -- 签约管理
UPDATE sys_menu SET sort = 13 WHERE id = 115;   -- 回款管理
UPDATE sys_menu SET sort = 14 WHERE id = 116;   -- 退换管理
UPDATE sys_menu SET sort = 15 WHERE id = 117;   -- 销售报表
