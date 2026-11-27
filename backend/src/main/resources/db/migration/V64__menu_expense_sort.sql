-- 费用管理左侧菜单顺序优化：总览/驾驶舱 → 主数据（预算/标准/合同）→ 业务单据（事项/申请/借款/报销/发票/付款）→ 分摊 → 分析
UPDATE sys_menu SET sort = 2 WHERE id = 131;  -- 费用预算
UPDATE sys_menu SET sort = 3 WHERE id = 132;  -- 费控标准
UPDATE sys_menu SET sort = 4 WHERE id = 128;  -- 费用合同
UPDATE sys_menu SET sort = 5 WHERE id = 129;  -- 事项申请
