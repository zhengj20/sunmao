-- =====================================================
-- V52 全面预算（年度+月度，成本+费用科目）
-- 管控链：年度全面预算 → 月度全面预算 → 资金计划（成本线/费用线）
--   B1 年度控制月度：Σ月度预算 ≤ 年度预算（硬控）
--   B2 年度下调约束：下调后年度额 ≥ Σ月度已分解 且 ≥ 已执行
--   B3 月度控制成本资金计划：成本付款申请提交时按合同科目分摊校验当月成本预算余额
--      （控制方式：刚性=阻断 / 预警=放行留痕 / 不限=跳过；无预算行=不控）
--   B4 月度控制费用资金计划：费用预算(部门×科目×月)合计 ≤ 同期月度费用预算
--   B5 资金计划一致性：成本线=合同付款计划(未付)、费用线=当月费用预算；超预算→资金缺口
-- 调整：年度增调/减调 + 月度增调/减调/跨月调剂，均留痕 bud_adjust
-- =====================================================

-- 1) 年度全面预算（成本线：项目×成本科目；费用线：部门×费用科目）
CREATE TABLE bud_budget (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  budget_code VARCHAR(64) NOT NULL COMMENT '预算编号 QYYS-{YYYY}-{seq}',
  budget_year INT NOT NULL COMMENT '预算年度',
  budget_type VARCHAR(16) NOT NULL COMMENT 'COST成本线/EXPENSE费用线',
  project_id BIGINT COMMENT '成本线：项目(mdm_project)',
  dept_id BIGINT COMMENT '费用线：部门(mdm_org)',
  account_id BIGINT NOT NULL COMMENT '科目(mdm_cost_account 末级)',
  account_name VARCHAR(128) COMMENT '科目名(冗余)',
  annual_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '年度预算额',
  allocated_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已分解(Σ月度预算)',
  control_mode VARCHAR(16) DEFAULT '刚性' COMMENT '刚性/预警/不限(默认继承科目)',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/APPROVED生效/ARCHIVED归档',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_bud_code (budget_code),
  UNIQUE KEY uk_bud_dim (budget_year, budget_type, project_id, dept_id, account_id),
  KEY idx_bud_type (budget_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度全面预算(年度控制月度)';

-- 2) 月度全面预算（年度预算的月度分解，B1 约束）
CREATE TABLE bud_budget_month (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  budget_id BIGINT NOT NULL COMMENT '年度预算',
  month INT NOT NULL COMMENT '1-12',
  month_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '月度预算额',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_bm (budget_id, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度全面预算(月度控制资金计划)';

-- 3) 预算调整单（调整留痕）
CREATE TABLE bud_adjust (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adjust_code VARCHAR(64) NOT NULL COMMENT '调整单号 TZ-{YYYY}-{seq}',
  budget_id BIGINT NOT NULL COMMENT '年度预算',
  month_id BIGINT COMMENT 'NULL=年度调整，非空=月度调整',
  adjust_type VARCHAR(16) NOT NULL COMMENT 'INCREASE调增/DECREASE调减',
  amount DECIMAL(16,2) NOT NULL COMMENT '调整金额',
  reason VARCHAR(500) NOT NULL COMMENT '调整原因',
  status VARCHAR(16) DEFAULT 'APPROVED' COMMENT 'APPROVED已生效/REJECTED已驳回(预留审批)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_adj_code (adjust_code),
  KEY idx_adj_budget (budget_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算调整单(年度/月度调整留痕)';

-- =====================================================
-- 编码规则 + 计数器
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('QYYS', '全面预算', 'QYYS-{YYYY}-{seq:4}', 'QYYS-2026-0001'),
('TZ',   '预算调整', 'TZ-{YYYY}-{seq:4}',   'TZ-2026-0001');
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('QYYS', DATE_FORMAT(CURDATE(), '%Y'), 0),
('TZ',   DATE_FORMAT(CURDATE(), '%Y'), 0);

-- =====================================================
-- 系统参数（管控开关）
-- =====================================================
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('bud.cost.strict',    'true', '成本月度预算管控开关(B3)', '成本付款申请提交时按合同科目校验当月成本预算，刚性科目超预算阻断'),
('bud.expense.strict', 'true', '费用月度预算管控开关(B4)', '费用预算编制/调整时校验同期月度费用预算，超预算按控制方式处理');

-- =====================================================
-- RBAC：全面预算目录(301) + 子菜单(311-313)
-- 位置：营销管理(80) 与 资金费用(90) 之间
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(301, 0,   '全面预算', 'D', '/budget',         NULL,                    'PieChart', 85),
(311, 301, '年度预算', 'M', '/budget/annual',  'budget/annual/index',   '', 1),
(312, 301, '月度预算', 'M', '/budget/month',   'budget/month/index',    '', 2),
(313, 301, '预算执行', 'M', '/budget/execute', 'budget/execute/index',  '', 3);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 301), (1, 311), (1, 312), (1, 313),
(2, 301), (2, 311), (2, 312), (2, 313),
(4, 301), (4, 311), (4, 312), (4, 313);

-- =====================================================
-- 种子数据：2026 年度全面预算（与既有演示数据自洽）
-- 成本线：项目1 星汇湾一期（科目取集团模板末级，与合同/付款计划一致）
-- 费用线：部门4/5（科目 8801/8803，与费用预算种子一致）
-- 说明：account_id 按 code 子查询引用（科目 id 自增，不能硬编码）
-- =====================================================

-- 成本：主体建筑工程 400,725,000（对齐目标成本 0302）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, project_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0001', 2026, 'COST', 1, a.id, a.name, 400725000.00, 114000000.00, '刚性', 'APPROVED',
       '主体建筑总承包(星汇湾一期)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '0302' AND a.project_id IS NULL AND a.deleted = 0;

-- 成本：土方及基坑 32,880,000（对齐目标成本 0301）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, project_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0002', 2026, 'COST', 1, a.id, a.name, 32880000.00, 32880000.00, '刚性', 'APPROVED',
       '土方及基坑工程(星汇湾一期)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '0301' AND a.project_id IS NULL AND a.deleted = 0;

-- 成本：主体安装 145,494,000（对齐目标成本 0303）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, project_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0003', 2026, 'COST', 1, a.id, a.name, 145494000.00, 20000000.00, '刚性', 'APPROVED',
       '机电安装总承包(星汇湾一期)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '0303' AND a.project_id IS NULL AND a.deleted = 0;

-- 成本：勘察设计 36,800,000（对齐目标成本 0201，预警科目）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, project_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0004', 2026, 'COST', 1, a.id, a.name, 36800000.00, 36800000.00, '预警', 'APPROVED',
       '勘察设计费(星汇湾一期)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '0201' AND a.project_id IS NULL AND a.deleted = 0;

-- 费用：部门4 差旅 2,400,000（刚性）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, dept_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0005', 2026, 'EXPENSE', 4, a.id, a.name, 2400000.00, 2400000.00, '刚性', 'APPROVED',
       '管理费用-差旅(年度)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '8801' AND a.project_id IS NULL AND a.deleted = 0;

-- 费用：部门5 市场推广 1,200,000（预警）
INSERT INTO bud_budget (budget_code, budget_year, budget_type, dept_id, account_id, account_name,
                        annual_amount, allocated_amount, control_mode, status, remark, created_at, updated_at)
SELECT 'QYYS-2026-0006', 2026, 'EXPENSE', 5, a.id, a.name, 1200000.00, 1200000.00, '预警', 'APPROVED',
       '销售费用-市场推广(年度)', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '8803' AND a.project_id IS NULL AND a.deleted = 0;

-- 编码计数器（对齐已造单量，避免撞号）
UPDATE mdm_code_counter SET current_no = 6 WHERE rule_code = 'QYYS' AND dim_key = DATE_FORMAT(CURDATE(), '%Y');

-- =====================================================
-- 月度分解种子（budget_id 按 维度+code 子查询引用，不能硬编码）
-- 主体建筑 0302：2月 2800万(预付) + 8月 5600万(进度) + 9月 3000万 = 1.14亿 ≤ 4.00725亿
-- =====================================================
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 2,  28000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0001';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 8,  56000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0001';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 9,  30000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0001';

-- 土方基坑 0301：1月 328万(预付) + 4月 1968万 + 5月 992万 = 3288万（对齐合同付款计划）
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 1,  3280000.00, 'ACTIVE', NOW(), NOW()  FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0002';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 4,  19680000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0002';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 5,  9920000.00, 'ACTIVE', NOW(), NOW()  FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0002';

-- 主体安装 0303：9月 2000万（机电安装拟9月开工）
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 9,  20000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0003';

-- 勘察设计 0201：1月1000 + 3月1000 + 5月1000 + 7月680（万）= 3680万
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 1,  10000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0004';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 3,  10000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0004';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 5,  10000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0004';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 7,   6800000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0004';

-- 费用 8801 部门4：1月100 + 2-7月各20 + 8月12 + 9月8（万）= 240万
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 1,  1000000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 2,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 3,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 4,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 5,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 6,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 7,   200000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 8,   120000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 9,    80000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0005';

-- 费用 8803 部门5：1月50 + 2-7月各10 + 8月10（万）= 120万
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 1,  500000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 2,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 3,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 4,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 5,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 6,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 7,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';
INSERT INTO bud_budget_month (budget_id, month, month_amount, status, created_at, updated_at)
SELECT b.id, 8,  100000.00, 'ACTIVE', NOW(), NOW() FROM bud_budget b WHERE b.budget_code = 'QYYS-2026-0006';

-- =====================================================
-- 调整单种子（演示留痕）
-- =====================================================
INSERT INTO bud_adjust (adjust_code, budget_id, month_id, adjust_type, amount, reason, status, created_at, updated_at)
SELECT 'TZ-2026-0001', b.id, m.id, 'INCREASE', 10000000.00, '主体建筑8月进度款追加（1#楼底板加厚签证确认，变更BG-PJ2026001-2026-0001）', 'APPROVED', NOW(), NOW()
FROM bud_budget b
JOIN bud_budget_month m ON m.budget_id = b.id AND m.month = 8
WHERE b.budget_code = 'QYYS-2026-0001' LIMIT 1;

UPDATE mdm_code_counter SET current_no = 1 WHERE rule_code = 'TZ' AND dim_key = DATE_FORMAT(CURDATE(), '%Y');
