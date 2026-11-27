-- =====================================================
-- V21 费用管理（对标明源云费用）：三费(管理/销售/财务) 全链
--   预算 → 标准 → 申请 → 借款 → 报销 → 发票池 → 付款 → 分摊 → 分析
-- 约定：金额 DECIMAL(16,2)；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- 硬校验规则：
--   R1 超预算阻断(申请/报销占用预算时余额不足默认阻断)
--   R2 发票查重(duplicate_hash 全平台唯一，重复阻断)
--   R3 报销超标自动计算 over_amount，超标须说明+特批
--   R4 借款额度控制(未冲销余额上限) + 逾期未冲销阻断新借款
-- =====================================================

-- 1) 费用预算（部门×科目×月）四栏：预算/占用/核销/余额
CREATE TABLE exp_budget (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dept_id BIGINT NOT NULL COMMENT '部门(mdm_org)',
  account_id BIGINT COMMENT '费用科目(mdm_cost_account)',
  account_name VARCHAR(128) COMMENT '费用科目名(冗余)',
  budget_year INT NOT NULL COMMENT '预算年度',
  month INT COMMENT '月份(1-12, NULL=年度)',
  amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '预算金额',
  occupy_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已占用(生效申请)',
  writeoff_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已核销(审批通过报销)',
  balance DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '余额=预算-占用-核销',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_bud_dept (dept_id), KEY idx_bud_account (account_id),
  KEY idx_bud_year (budget_year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用预算台账(部门×科目×月)';

-- 2) 费控标准库（职级×城市等级×类型）
CREATE TABLE exp_standard (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  level VARCHAR(32) NOT NULL COMMENT '职级(E5/E6/E7/M3/M4/M5)',
  city_level VARCHAR(32) NOT NULL COMMENT '城市等级(一线/二线/三线/全部)',
  type VARCHAR(32) NOT NULL COMMENT 'TRAVEL住宿交通/HOST招待/COMMS通讯/CAR车辆',
  limit_amount DECIMAL(16,2) NOT NULL COMMENT '限额标准(金额)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_std_level (level), KEY idx_std_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费控标准(职级×城市×类型)';

-- 3) 费用事前申请
CREATE TABLE exp_apply (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL COMMENT '申请单号',
  type VARCHAR(32) NOT NULL COMMENT 'TRAVEL出差/HOST招待/PROCURE采购',
  dept_id BIGINT NOT NULL,
  user_id BIGINT COMMENT '申请人',
  user_name VARCHAR(64),
  amount DECIMAL(16,2) NOT NULL COMMENT '申请金额',
  budget_id BIGINT COMMENT '占用预算',
  occupy_amount DECIMAL(16,2) DEFAULT 0 COMMENT '实际占用预算额',
  remark VARCHAR(500),
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/ACTIVE/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_apply_no (apply_no),
  KEY idx_apply_dept (dept_id), KEY idx_apply_budget (budget_id), KEY idx_apply_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用事前申请';

-- 4) 借款
CREATE TABLE exp_loan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loan_no VARCHAR(64) NOT NULL COMMENT '借据号',
  user_id BIGINT NOT NULL COMMENT '借款人',
  user_name VARCHAR(64),
  amount DECIMAL(16,2) NOT NULL COMMENT '借款金额',
  unwriteoff_balance DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '未冲销余额',
  borrow_date DATE COMMENT '借款日期',
  due_date DATE COMMENT '应还/应冲销日期',
  status VARCHAR(32) DEFAULT 'APPROVING' COMMENT 'APPROVING/ACTIVE/WRITEOFF/DUE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_loan_no (loan_no),
  KEY idx_loan_user (user_id), KEY idx_loan_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款(额度控制R4)';

-- 5) 报销单
CREATE TABLE exp_reimburse (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reimb_no VARCHAR(64) NOT NULL COMMENT '报销单号',
  type VARCHAR(32) COMMENT 'TRAVEL差旅/DAILY日常',
  dept_id BIGINT NOT NULL,
  user_id BIGINT,
  user_name VARCHAR(64),
  total_amount DECIMAL(16,2) NOT NULL COMMENT '报销总额',
  over_amount DECIMAL(16,2) DEFAULT 0 COMMENT '超标额(实际-标准)',
  over_reason VARCHAR(500) COMMENT '超标原因(R3)',
  apply_id BIGINT COMMENT '关联事前申请',
  loan_id BIGINT COMMENT '冲销借款',
  invoice_id BIGINT COMMENT '关联发票',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/PAID',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_reimb_no (reimb_no),
  KEY idx_reimb_dept (dept_id), KEY idx_reimb_user (user_id), KEY idx_reimb_apply (apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销单';

-- 6) 报销明细行
CREATE TABLE exp_reimburse_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reimb_id BIGINT NOT NULL,
  account_id BIGINT COMMENT '费用科目',
  account_name VARCHAR(128),
  amount DECIMAL(16,2) NOT NULL COMMENT '明细金额',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_item_reimb (reimb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销明细行';

-- 7) 发票池（全集团进项：报销/成本/采购）
CREATE TABLE exp_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  inv_no VARCHAR(64) NOT NULL COMMENT '发票号',
  tax_no VARCHAR(64) COMMENT '发票代码/税号',
  supplier VARCHAR(128) COMMENT '供应商',
  amount DECIMAL(16,2) NOT NULL COMMENT '价税合计',
  tax_amount DECIMAL(16,2) COMMENT '税额',
  verify_status VARCHAR(16) DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/PASS/FAIL',
  duplicate_hash VARCHAR(64) COMMENT '查重hash(全平台唯一)',
  source VARCHAR(32) DEFAULT 'REIMBURSE' COMMENT 'REIMBURSE/COST/PROC',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/VOID',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_inv_hash (duplicate_hash),
  KEY idx_inv_supplier (supplier), KEY idx_inv_status (verify_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票池(全集团进项,查重R2)';

-- 8) 报销付款单
CREATE TABLE exp_payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pay_no VARCHAR(64) NOT NULL COMMENT '付款单号',
  reimb_id BIGINT NOT NULL COMMENT '关联报销单',
  amount DECIMAL(16,2) NOT NULL COMMENT '实付金额',
  paid_date DATE COMMENT '付款日期',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/PAID',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_pay_reimb (reimb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销付款单';

-- 9) 费用分摊
CREATE TABLE exp_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  alloc_no VARCHAR(64) COMMENT '分摊单号',
  month VARCHAR(7) COMMENT '分摊月(yyyy-MM)',
  rule_text VARCHAR(500) COMMENT '分摊规则文本',
  dept_id BIGINT COMMENT '分摊目标部门',
  project_id BIGINT COMMENT '分摊目标项目',
  amount DECIMAL(16,2) COMMENT '分摊金额',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_alloc_month (month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用分摊';

-- =====================================================
-- 费用科目（三费：管理/销售/财务，复用 mdm_cost_account 科目树）
-- 说明：不指定主键 id（V8 已把集团模板克隆到项目1，id 17+ 已被占用），由自增分配，
--       后续种子/代码按 code 引用
-- =====================================================
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort) VALUES
(NULL, 0, '88',   '期间费用',          1, 0, '刚性', 7);
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort)
SELECT NULL, (SELECT id FROM mdm_cost_account WHERE code = '88' AND project_id IS NULL AND deleted = 0 LIMIT 1), '8801', '管理费用-差旅',     2, 1, '刚性', 1;
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort)
SELECT NULL, (SELECT id FROM mdm_cost_account WHERE code = '88' AND project_id IS NULL AND deleted = 0 LIMIT 1), '8802', '管理费用-业务招待', 2, 1, '预警', 2;
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort)
SELECT NULL, (SELECT id FROM mdm_cost_account WHERE code = '88' AND project_id IS NULL AND deleted = 0 LIMIT 1), '8803', '销售费用-市场推广', 2, 1, '预警', 3;
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort)
SELECT NULL, (SELECT id FROM mdm_cost_account WHERE code = '88' AND project_id IS NULL AND deleted = 0 LIMIT 1), '8804', '财务费用-利息',     2, 1, '刚性', 4;

-- =====================================================
-- 编码规则 + 计数器（YEAR 维度，与 CodeRuleService 一致）
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('YSSZ', '费用预算', 'YSSZ-{YYYY}-{seq:4}', 'YSSZ-2026-0001'),
('YSSY', '费用申请', 'YSSY-{YYYY}-{seq:4}', 'YSSY-2026-0001'),
('JK',   '借款借据', 'JK-{YYYY}-{seq:4}',   'JK-2026-0001'),
('BX',   '报销单',   'BX-{YYYY}-{seq:4}',   'BX-2026-0001'),
('FKP',  '费用付款', 'FKP-{YYYY}-{seq:4}',  'FKP-2026-0001');

-- =====================================================
-- 工作流模板：EXP_BUDGET(预算) / EXP_LOAN(借款) / EXP_REIMB_OVERRUN(报销超标特批)
-- 审批角色：FIN_MGR(财务经理) / ADMIN(集团)
-- =====================================================
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_EXP_BUDGET', '费用预算审批流', 'EXP_BUDGET',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"财务经理","assigneeType":"ROLE","assignees":["FIN_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 '预算编制：财务→集团会签'),
('TPL_EXP_LOAN', '借款审批流', 'EXP_LOAN',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"部门经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"财务经理","assigneeType":"ROLE","assignees":["FIN_MGR"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 '借款：部门→财务'),
('TPL_EXP_REIMB_OVERRUN', '报销超标特批流', 'EXP_REIMB_OVERRUN',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"部门经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"总经理","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 'R3 超标须经理→总经理特批');

-- =====================================================
-- RBAC：菜单 + admin/财务角色授权（菜单 id 13 目录，131-139 子菜单）
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(13,  0,   '费用管理',   'D', '/expense',      NULL,                      'Wallet',   35),
(131, 13,  '费用预算',   'M', '/expense/budget',      'expense/budget/index',      '', 1),
(132, 13,  '费控标准',   'M', '/expense/standard',    'expense/standard/index',    '', 2),
(133, 13,  '费用申请',   'M', '/expense/apply',       'expense/apply/index',       '', 3),
(134, 13,  '借款管理',   'M', '/expense/loan',        'expense/loan/index',        '', 4),
(135, 13,  '报销管理',   'M', '/expense/reimburse',   'expense/reimburse/index',   '', 5),
(136, 13,  '发票池',     'M', '/expense/invoice',     'expense/invoice/index',     '', 6),
(137, 13,  '费用付款',   'M', '/expense/payment',     'expense/payment/index',     '', 7),
(138, 13,  '费用分摊',   'M', '/expense/allocation',  'expense/allocation/index',  '', 8),
(139, 13,  '费用分析',   'M', '/expense/report',      'expense/report/index',      '', 9);

-- admin(1) 全费用菜单；财务经理(4) 全费用菜单 + 工作台/审批
-- INSERT IGNORE：角色可能已被早期迁移授予部分菜单(如 4-1)，幂等跳过重复
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 13), (1, 131), (1, 132), (1, 133), (1, 134), (1, 135), (1, 136), (1, 137), (1, 138), (1, 139);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (13, 131, 132, 133, 134, 135, 136, 137, 138, 139, 1, 8, 7, 81, 82, 83);

-- admin 追加 成本经理/财务经理 角色：使其可审批费用各审批节点（演示需要，幂等）
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2), (1, 4)
ON DUPLICATE KEY UPDATE user_id = user_id;

-- =====================================================
-- 系统参数（业务规则阈值）
-- =====================================================
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('exp.budget.strict',        'true',   '超预算阻断开关(R1)',     '申请/报销占用预算时余额不足默认阻断'),
('exp.invoice.dedup',        'true',   '发票查重开关(R2)',       'duplicate_hash 全平台唯一，重复阻断'),
('exp.loan.max_balance',     '100000', '借款未冲销余额上限(元)', 'R4 额度控制：同一借款人未冲销余额合计上限'),
('exp.loan.due_block',       'true',   '逾期未冲销阻断新借款(R4)', '存在逾期(DUE)未冲销借款时阻断新借款');

-- =====================================================
-- 种子数据（确保预算台账四栏/发票池/报表分析有数）
-- 说明：不造假流水，仅构造可演示的单据主数据
-- =====================================================

-- 预算台账（部门4/5 × 科目，四栏自洽：余额=预算-占用-核销）
-- account_id 按 code 子查询引用（科目 id 自增，不能硬编码）
INSERT INTO exp_budget (dept_id, account_id, account_name, budget_year, month, amount, occupy_amount, writeoff_amount, balance, status, created_at, updated_at)
SELECT 4, a.id, a.name, 2026, 1, 1000000.00, 100000.00, 50000.00, 850000.00, 'ACTIVE', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '8801' AND a.project_id IS NULL AND a.deleted = 0;
INSERT INTO exp_budget (dept_id, account_id, account_name, budget_year, month, amount, occupy_amount, writeoff_amount, balance, status, created_at, updated_at)
SELECT 4, a.id, a.name, 2026, 1, 200000.00, 0.00, 0.00, 200000.00, 'ACTIVE', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '8802' AND a.project_id IS NULL AND a.deleted = 0;
INSERT INTO exp_budget (dept_id, account_id, account_name, budget_year, month, amount, occupy_amount, writeoff_amount, balance, status, created_at, updated_at)
SELECT 5, a.id, a.name, 2026, 1, 500000.00, 0.00, 0.00, 500000.00, 'ACTIVE', NOW(), NOW()
FROM mdm_cost_account a WHERE a.code = '8803' AND a.project_id IS NULL AND a.deleted = 0;

-- 费控标准（职级×城市×类型）
INSERT INTO exp_standard (level, city_level, type, limit_amount, created_at, updated_at) VALUES
('E7', '一线', 'TRAVEL', 800.00, NOW(), NOW()),
('M3', '一线', 'HOST',   1500.00, NOW(), NOW()),
('M4', '全部', 'CAR',    2000.00, NOW(), NOW());

-- 事前申请（ACTIVE，已占用部门4/差旅预算 100000）
-- budget_id 按 部门+科目code 子查询引用（预算 id 自增，不能硬编码）
INSERT INTO exp_apply (apply_no, type, dept_id, user_id, user_name, amount, budget_id, occupy_amount, remark, status, created_at, updated_at)
SELECT 'YSSY-2026-0001', 'TRAVEL', 4, 1, '系统管理员', 100000.00, b.id, 100000.00, '项目拓展出差', 'ACTIVE', NOW(), NOW()
FROM exp_budget b JOIN mdm_cost_account a ON b.account_id = a.id
WHERE b.dept_id = 4 AND b.budget_year = 2026 AND a.code = '8801'
LIMIT 1;

-- 借款（ACTIVE，未冲销余额 30000；借款人张成/成本经理，便于 admin 演示 R4）
INSERT INTO exp_loan (loan_no, user_id, user_name, amount, unwriteoff_balance, borrow_date, due_date, status, created_at, updated_at) VALUES
('JK-2026-0001', 2, '张成', 30000.00, 30000.00, DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'ACTIVE', NOW(), NOW());

-- 发票池（1 张，验真 PASS；duplicate_hash = SHA2(税号|发票号)）
INSERT INTO exp_invoice (inv_no, tax_no, supplier, amount, tax_amount, verify_status, duplicate_hash, source, status, created_at, updated_at) VALUES
('FP2026090001', '91330100MA27XYZ123', '杭州某某酒店有限公司', 45000.00, 2610.00, 'PASS',
 SHA2(CONCAT('91330100MA27XYZ123', '|', 'FP2026090001'), 256), 'REIMBURSE', 'ACTIVE', NOW(), NOW());

-- 报销单（APPROVED，超标 over_amount 5000，含 2 条明细，关联发票；核销部门4/差旅预算 50000）
INSERT INTO exp_reimburse (reimb_no, type, dept_id, user_id, user_name, total_amount, over_amount, over_reason, apply_id, loan_id, invoice_id, status, created_at, updated_at)
SELECT 'BX-2026-0001', 'TRAVEL', 4, 1, '系统管理员', 50000.00, 5000.00, '项目紧急拓展，超标部分已说明', a.id, NULL, i.id, 'APPROVED', NOW(), NOW()
FROM exp_apply a, exp_invoice i WHERE a.apply_no = 'YSSY-2026-0001' AND i.inv_no = 'FP2026090001';

-- 报销明细行（2 条，合计 50000）
-- account_id 按 code 子查询引用（科目 id 自增，不能硬编码）
INSERT INTO exp_reimburse_item (reimb_id, account_id, account_name, amount, remark, created_at, updated_at)
SELECT r.id, a.id, a.name, 45000.00, '差旅住宿/交通', NOW(), NOW()
FROM exp_reimburse r, mdm_cost_account a
WHERE r.reimb_no = 'BX-2026-0001' AND a.code = '8801' AND a.project_id IS NULL AND a.deleted = 0;
INSERT INTO exp_reimburse_item (reimb_id, account_id, account_name, amount, remark, created_at, updated_at)
SELECT r.id, a.id, a.name, 5000.00, '业务招待', NOW(), NOW()
FROM exp_reimburse r, mdm_cost_account a
WHERE r.reimb_no = 'BX-2026-0001' AND a.code = '8802' AND a.project_id IS NULL AND a.deleted = 0;

-- 报销核销回写预算（部门4/差旅 核销 50000，已在上方预算 seed 中体现 writeoff=50000/balance=850000）

-- 编码计数器（按已造单量初始化，避免新单撞号）
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('YSSZ', DATE_FORMAT(CURDATE(), '%Y'), 3),
('YSSY', DATE_FORMAT(CURDATE(), '%Y'), 1),
('JK',   DATE_FORMAT(CURDATE(), '%Y'), 1),
('BX',   DATE_FORMAT(CURDATE(), '%Y'), 1),
('FKP',  DATE_FORMAT(CURDATE(), '%Y'), 0)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));
