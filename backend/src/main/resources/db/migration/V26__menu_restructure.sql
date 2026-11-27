-- =====================================================
-- V26 菜单信息架构重排
--   1. 资金(6) + 费用(13) 合并为「资金费用」，费用子项挂入资金下
--   2. 一级目录按房地产业务流重排（10 的倍数留扩展间隔，消除 sort 撞车）
--   3. 个别一级目录更名更贴近业务（经营驾驶舱/投资管理/营销管理）
-- 说明：仅调整 sys_menu 的 name/parent_id/sort/visible，不动 path/component，
--       前端路由与页面零改动；myMenus 已由后端 buildTree 组装树。
-- =====================================================

-- 1) 费用(13) 的 9 个子项迁移到 资金(6) 下，sort 接在资金 3 项之后
UPDATE sys_menu SET parent_id = 6 WHERE id IN (131, 132, 133, 134, 135, 136, 137, 138, 139);
UPDATE sys_menu SET sort = 4  WHERE id = 131;  -- 费用预算
UPDATE sys_menu SET sort = 5  WHERE id = 132;  -- 费控标准
UPDATE sys_menu SET sort = 6  WHERE id = 133;  -- 费用申请
UPDATE sys_menu SET sort = 7  WHERE id = 134;  -- 借款管理
UPDATE sys_menu SET sort = 8  WHERE id = 135;  -- 报销管理
UPDATE sys_menu SET sort = 9  WHERE id = 136;  -- 发票池
UPDATE sys_menu SET sort = 10 WHERE id = 137;  -- 费用付款
UPDATE sys_menu SET sort = 11 WHERE id = 138;  -- 费用分摊
UPDATE sys_menu SET sort = 12 WHERE id = 139;  -- 费用分析

-- 2) 隐藏已并空的「费用管理」目录（其子项已迁走，避免空目录可点）
UPDATE sys_menu SET visible = 0 WHERE id = 13;

-- 3) 合并后目录更名 + 个别一级目录更名
UPDATE sys_menu SET name = '资金费用'   WHERE id = 6;
UPDATE sys_menu SET name = '经营驾驶舱' WHERE id = 8;
UPDATE sys_menu SET name = '投资管理'   WHERE id = 5;
UPDATE sys_menu SET name = '营销管理'   WHERE id = 11;

-- 4) 13 个一级目录按业务流重排 sort
UPDATE sys_menu SET sort = 10  WHERE id = 1;    -- 工作台
UPDATE sys_menu SET sort = 20  WHERE id = 8;    -- 经营驾驶舱
UPDATE sys_menu SET sort = 30  WHERE id = 7;    -- 审批中心
UPDATE sys_menu SET sort = 40  WHERE id = 5;    -- 投资管理
UPDATE sys_menu SET sort = 50  WHERE id = 3;    -- 招采管理
UPDATE sys_menu SET sort = 60  WHERE id = 4;    -- 成本管理
UPDATE sys_menu SET sort = 70  WHERE id = 14;   -- 项目计划
UPDATE sys_menu SET sort = 80  WHERE id = 11;   -- 营销管理
UPDATE sys_menu SET sort = 90  WHERE id = 6;    -- 资金费用
UPDATE sys_menu SET sort = 100 WHERE id = 2;    -- 主数据
UPDATE sys_menu SET sort = 110 WHERE id = 500;  -- 数字员工
UPDATE sys_menu SET sort = 120 WHERE id = 9;    -- 系统管理
UPDATE sys_menu SET sort = 130 WHERE id = 10;   -- 集成平台
