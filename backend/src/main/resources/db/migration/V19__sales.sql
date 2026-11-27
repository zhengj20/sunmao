-- =====================================================
-- V19 销售管理（明源销售对标）：房源销控 / 认购 / 签约 / 应收 / 实收 / 按揭 / 退换 / 折扣 / 案场来访
-- 约定：金额统一 DECIMAL(16,2)；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- 一房一档：同 room_id 同一时刻仅一条有效(status<>'REFUNDED')，普通索引+代码层乐观锁校验（不强唯一键）
-- =====================================================

-- 1) 房源销控状态（一房一档）
CREATE TABLE sale_room_status (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  room_id BIGINT NOT NULL COMMENT '关联 mdm_room',
  room_code VARCHAR(64) COMMENT '房间编号(冗余)',
  building_id BIGINT,
  floor_no INT,
  unit_no INT,
  area DECIMAL(14,2) COMMENT '建筑面积㎡(冗余)',
  table_price DECIMAL(16,2) COMMENT '表价(总价)',
  floor_price DECIMAL(16,2) COMMENT '底价(总价)',
  status VARCHAR(32) DEFAULT 'SELLABLE' COMMENT 'SELLABLE可售/LOCKED锁定/SMALL_DEPOSIT小订/BIG_DEPOSIT大定/SUBSCRIBED认购/CONTRACTED签约/FILED网签/REFUNDED退房',
  lock_by BIGINT COMMENT '锁定人',
  lock_expire DATETIME COMMENT '锁定到期',
  biz_no VARCHAR(64) COMMENT '关联单据号(认购/签约)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_rs_project (project_id),
  KEY idx_rs_room (room_id),
  KEY idx_rs_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源销控(一房一档)';

-- 2) 认购
CREATE TABLE sale_subscription (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sub_no VARCHAR(64) NOT NULL COMMENT '认购单号',
  project_id BIGINT NOT NULL,
  room_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  deposit DECIMAL(16,2) COMMENT '定金金额',
  agent_id BIGINT COMMENT '置业顾问',
  expected_sign_date DATE COMMENT '预计签约日',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/ACTIVE/CONVERTED/REJECTED/TERMINATED',
  contract_id BIGINT COMMENT '转签约生成的合同',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_sub_no (sub_no),
  KEY idx_sub_project (project_id), KEY idx_sub_room (room_id), KEY idx_sub_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认购';

-- 3) 销售合同
CREATE TABLE sale_contract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_no VARCHAR(64) NOT NULL COMMENT '合同号',
  project_id BIGINT NOT NULL,
  room_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  contract_price DECIMAL(16,2) NOT NULL COMMENT '成交价',
  pay_method VARCHAR(32) COMMENT 'ONE_TIME一次性/MORTGAGE按揭/INSTALLMENT分期',
  discount_rate DECIMAL(5,4) COMMENT '成交折扣率(成交价/表价)',
  subscription_id BIGINT COMMENT '来源认购单',
  supplier_id BIGINT COMMENT '中介(供应商)',
  supplier_name VARCHAR(128) COMMENT '中介名称',
  signed_at DATE COMMENT '签约日期',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/ACTIVE/FILED/REFUNDED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_contract_no (contract_no),
  KEY idx_c_project (project_id), KEY idx_c_room (room_id), KEY idx_c_customer (customer_id), KEY idx_c_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售合同';

-- 4) 应收计划
CREATE TABLE sale_payment_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  seq INT COMMENT '期次',
  term_desc VARCHAR(128) COMMENT '期次说明(签约款/按揭款/分期)',
  plan_amount DECIMAL(16,2) NOT NULL COMMENT '应收金额',
  received_amount DECIMAL(16,2) DEFAULT 0 COMMENT '已收金额(核销)',
  due_date DATE COMMENT '到期日',
  status VARCHAR(32) DEFAULT 'DUE' COMMENT 'DUE待收/RECEIVED已收/OVERDUE逾期',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_pp_contract (contract_id), KEY idx_pp_status (status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收计划';

-- 5) 实收
CREATE TABLE sale_receipt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  receipt_no VARCHAR(64) NOT NULL COMMENT '实收单号',
  contract_id BIGINT NOT NULL,
  plan_id BIGINT COMMENT '核销的应收计划',
  received_amount DECIMAL(16,2) NOT NULL COMMENT '实收金额',
  pay_way VARCHAR(32) COMMENT 'POS/转账/现金',
  ticket_no VARCHAR(64) COMMENT '票据号',
  received_at DATETIME COMMENT '收款时间',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_receipt_no (receipt_no),
  KEY idx_r_contract (contract_id), KEY idx_r_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实收登记';

-- 6) 按揭
CREATE TABLE sale_mortgage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  bank_name VARCHAR(128) COMMENT '银行',
  mortgage_amount DECIMAL(16,2) COMMENT '按揭金额',
  stage VARCHAR(32) DEFAULT 'APPLY' COMMENT 'APPLY申请/APPROVE审批/MORTGAGE抵押/DISBURSE放款',
  disbursed_at DATE COMMENT '放款日期',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_m_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按揭';

-- 7) 退换（退定/退房/换房）
CREATE TABLE sale_refund_exchange (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_no VARCHAR(64) NOT NULL COMMENT '退换单号',
  project_id BIGINT NOT NULL,
  contract_id BIGINT COMMENT '退房/换房关联合同',
  subscription_id BIGINT COMMENT '退定关联认购',
  room_id BIGINT,
  type VARCHAR(32) COMMENT 'REFUND退定/退房,EXCHANGE换房',
  refund_amount DECIMAL(16,2) COMMENT '退还金额',
  reason VARCHAR(500),
  reason_category VARCHAR(64) COMMENT '原因归类',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_refund_project (project_id), KEY idx_refund_contract (contract_id), KEY idx_refund_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退换申请';

-- 8) 折扣方案
CREATE TABLE sale_discount (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  discount_no VARCHAR(64) NOT NULL COMMENT '折扣方案号',
  name VARCHAR(128) NOT NULL COMMENT '方案名称',
  scope VARCHAR(64) COMMENT '适用范围(项目/业态/全部)',
  base_rate DECIMAL(5,4) COMMENT '基准折扣率',
  max_rate DECIMAL(5,4) COMMENT '该级别最大折扣率(可下探下限)',
  level VARCHAR(32) COMMENT 'AGENT置业顾问/MGR经理/GM总经理',
  effective_from DATE,
  effective_to DATE,
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_discount_no (discount_no),
  KEY idx_discount_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='折扣方案(分级权限)';

-- 9) 案场来访
CREATE TABLE sale_visit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  visit_no VARCHAR(64) NOT NULL COMMENT '来访单号',
  project_id BIGINT NOT NULL,
  customer_name VARCHAR(64) NOT NULL COMMENT '客户姓名/手机号(判客键)',
  channel VARCHAR(32) COMMENT '渠道(自然/中介/老带新/线上)',
  agent_id BIGINT COMMENT '置业顾问',
  intent_level VARCHAR(16) COMMENT 'HIGH/MID/LOW',
  visit_at DATETIME COMMENT '来访时间',
  status VARCHAR(16) DEFAULT 'REGISTERED' COMMENT 'REGISTERED/PROTECTED/LOST',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_visit_no (visit_no),
  KEY idx_visit_project (project_id), KEY idx_visit_customer (project_id, customer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案场来访(判客保护期)';

-- =====================================================
-- 编码规则 + 计数器（dim_key 与 CodeRuleService 的 YEAR 维度一致）
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('RG',  '认购单',   'RG-{projectCode}-{YYYY}-{seq:4}',    'RG-PJ2026001-2026-0001'),
('XHT', '销售合同', 'XHT-{projectCode}-{YYYY}-{seq:4}',   'XHT-PJ2026001-2026-0001'),
('SH',  '实收单',   'SH-{projectCode}-{YYYY}-{seq:4}',    'SH-PJ2026001-2026-0001'),
('TH',  '退换单',   'TH-{projectCode}-{YYYY}-{seq:4}',    'TH-PJ2026001-2026-0001'),
('ZK',  '折扣方案', 'ZK-{projectCode}-{YYYY}-{seq:4}',    'ZK-PJ2026001-2026-0001'),
('LF',  '案场来访', 'LF-{YYYY}{MM}-{seq:4}',              'LF-202609-0001');

-- =====================================================
-- 工作流审批模板（销售 bizType：SALE_REFUND / SALE_SUBSCRIPTION_TERMINATE / SALE_SPECIAL_PRICE）
-- 审批人：首节点销售经理(SALES_MGR)，大额(≥50万)上集团会签(ADMIN)
-- =====================================================
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_SALE_REFUND', '退房/换房审批流', 'SALE_REFUND',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"销售经理","assigneeType":"ROLE","assignees":["SALES_MGR"],"countersign":"ANY"},{"id":"n2","type":"CONDITION","name":"金额路由","branches":[{"when":{"field":"amount","op":">=","value":500000},"next":"n3"},{"next":"n4"}]},{"id":"n3","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}', '≥50万上集团会签'),
('TPL_SALE_SUB_TERMINATE', '认购退定审批流', 'SALE_SUBSCRIPTION_TERMINATE',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"销售经理","assigneeType":"ROLE","assignees":["SALES_MGR"],"countersign":"ANY"},{"id":"n2","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_SALE_SPECIAL_PRICE', '特价/超权限折扣审批流', 'SALE_SPECIAL_PRICE',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"销售经理","assigneeType":"ROLE","assignees":["SALES_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}', '超权限折扣必须集团会签');

-- =====================================================
-- RBAC：菜单 + 销售角色 + 用户 + 授权
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(11,  0,   '销售管理',   'D', '/sales',      NULL,                      'Sell',       25),
(111, 11,  '房源销控',   'M', '/sales/room',        'sales/room/index',        '', 1),
(112, 11,  '价格折扣',   'M', '/sales/discount',    'sales/discount/index',    '', 2),
(113, 11,  '认购管理',   'M', '/sales/subscription','sales/subscription/index','', 3),
(114, 11,  '签约管理',   'M', '/sales/contract',    'sales/contract/index',    '', 4),
(115, 11,  '回款管理',   'M', '/sales/payment',     'sales/payment/index',     '', 5),
(116, 11,  '退换管理',   'M', '/sales/refund',      'sales/refund/index',      '', 6),
(117, 11,  '销售报表',   'M', '/sales/report',      'sales/report/index',      '', 7);

INSERT INTO sys_role (id, name, code, remark) VALUES
(6, '销售经理', 'SALES_MGR', '销售管理/审批');

-- admin 拥有全部销售菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 11), (1, 111), (1, 112), (1, 113), (1, 114), (1, 115), (1, 116), (1, 117);
-- 销售经理：销售菜单 + 工作台/驾驶舱/审批待办已办/我发起
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, id FROM sys_menu WHERE id IN (11, 111, 112, 113, 114, 115, 116, 117, 1, 8, 7, 81, 82, 83);

-- 销售经理账号（可登录演示）
INSERT INTO sys_user (id, username, password, real_name, mobile, status, org_id, job_level) VALUES
(6, 'salesmgr', 'PLAIN:123456', '钱销售', '13800000006', 1, 4, 'E7');
INSERT INTO sys_user_role (user_id, role_id) VALUES (6, 6), (1, 6);

-- =====================================================
-- 系统参数（业务规则阈值）
-- =====================================================
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('sales.visit.protection_days', '30',     '案场判客保护期(天)', '报备后N天内同一客户不可重复报备'),
('sales.discount.strict',       'true',   '折扣刚性校验开关',   '超权限成交折扣须关联特价审批');

-- =====================================================
-- 种子数据（项目1，确保 /reports/overview 出数）
-- =====================================================
-- 房源销控：项目1全部可售房间 → SELLABLE（表价/底价按 29800 / 26800 元/㎡ 估算）
INSERT INTO sale_room_status (project_id, room_id, room_code, building_id, floor_no, unit_no, area, table_price, floor_price, status, created_at, updated_at)
SELECT r.project_id, r.id, r.room_code, r.building_id, r.floor_no, r.unit_no, r.area,
       ROUND(r.area * 29800, 2), ROUND(r.area * 26800, 2), 'SELLABLE', NOW(), NOW()
FROM mdm_room r WHERE r.project_id = 1 AND r.deleted = 0;

-- 折扣方案（AGENT/MGR 各一，GM 默认最高）
INSERT INTO sale_discount (discount_no, name, scope, base_rate, max_rate, level, effective_from, effective_to, status, created_at, updated_at) VALUES
('ZK-PJ2026001-2026-0001', '置业顾问标准折扣', '全部', 0.9800, 0.9500, 'AGENT', '2026-01-01', '2026-12-31', 'ACTIVE', NOW(), NOW()),
('ZK-PJ2026001-2026-0002', '经理特价折扣',     '全部', 0.9800, 0.9000, 'MGR',   '2026-01-01', '2026-12-31', 'ACTIVE', NOW(), NOW());

-- 案场来访（3条）
INSERT INTO sale_visit (visit_no, project_id, customer_name, channel, agent_id, intent_level, visit_at, status, created_at, updated_at) VALUES
('LF-202609-0001', 1, '13800001001', '自然来访', 2, 'HIGH', '2026-09-01 10:20:00', 'REGISTERED', NOW(), NOW()),
('LF-202609-0002', 1, '13800001002', '中介',     2, 'MID',  '2026-09-02 14:40:00', 'REGISTERED', NOW(), NOW()),
('LF-202609-0003', 1, '13800001003', '老带新',   2, 'LOW',  '2026-09-03 09:10:00', 'REGISTERED', NOW(), NOW());

-- 认购（ACTIVE）：房间 1#-001
INSERT INTO sale_subscription (sub_no, project_id, room_id, customer_id, deposit, agent_id, expected_sign_date, status, created_at, updated_at)
SELECT 'RG-PJ2026001-2026-0001', 1, r.id, 1, 50000.00, 2, DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'ACTIVE', NOW(), NOW()
FROM mdm_room r WHERE r.room_code = 'PJ2026001-1#-001' AND r.project_id = 1 AND r.deleted = 0 LIMIT 1;
UPDATE sale_room_status SET status = 'SUBSCRIBED', biz_no = 'RG-PJ2026001-2026-0001'
WHERE room_id = (SELECT id FROM mdm_room WHERE room_code = 'PJ2026001-1#-001' AND project_id = 1 AND deleted = 0);

-- 签约（ACTIVE，按揭）：房间 1#-002，成交价 3,967,200（≈95.6折）
INSERT INTO sale_contract (contract_no, project_id, room_id, customer_id, contract_price, pay_method, discount_rate, signed_at, status, created_at, updated_at)
SELECT 'XHT-PJ2026001-2026-0001', 1, r.id, 2, 3967200.00, 'MORTGAGE', 0.9564, CURDATE(), 'ACTIVE', NOW(), NOW()
FROM mdm_room r WHERE r.room_code = 'PJ2026001-1#-002' AND r.project_id = 1 AND r.deleted = 0 LIMIT 1;
UPDATE sale_room_status SET status = 'CONTRACTED', biz_no = 'XHT-PJ2026001-2026-0001'
WHERE room_id = (SELECT id FROM mdm_room WHERE room_code = 'PJ2026001-1#-002' AND project_id = 1 AND deleted = 0);

-- 应收计划（按揭：签约款20% + 按揭款70% + 尾款10%）
INSERT INTO sale_payment_plan (contract_id, seq, term_desc, plan_amount, received_amount, due_date, status, created_at, updated_at)
SELECT c.id, 1, '签约款(20%)', 793440.00, 793440.00, DATE_SUB(CURDATE(), INTERVAL 30 DAY), 'RECEIVED', NOW(), NOW()
FROM sale_contract c WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';
INSERT INTO sale_payment_plan (contract_id, seq, term_desc, plan_amount, received_amount, due_date, status, created_at, updated_at)
SELECT c.id, 2, '按揭款(70%)', 2777040.00, 0, DATE_ADD(CURDATE(), INTERVAL 60 DAY), 'DUE', NOW(), NOW()
FROM sale_contract c WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';
INSERT INTO sale_payment_plan (contract_id, seq, term_desc, plan_amount, received_amount, due_date, status, created_at, updated_at)
SELECT c.id, 3, '尾款(10%)', 396720.00, 396720.00, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'RECEIVED', NOW(), NOW()
FROM sale_contract c WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';

-- 实收（2笔，对应签约款/尾款）
INSERT INTO sale_receipt (receipt_no, contract_id, plan_id, received_amount, pay_way, ticket_no, received_at, created_at, updated_at)
SELECT 'SH-PJ2026001-2026-0001', c.id, p.id, 793440.00, '转账', 'TK20260801-001', '2026-08-01 11:00:00', NOW(), NOW()
FROM sale_contract c JOIN sale_payment_plan p ON p.contract_id = c.id AND p.seq = 1
WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';
INSERT INTO sale_receipt (receipt_no, contract_id, plan_id, received_amount, pay_way, ticket_no, received_at, created_at, updated_at)
SELECT 'SH-PJ2026001-2026-0002', c.id, p.id, 396720.00, 'POS', 'TK20260820-002', '2026-08-20 15:30:00', NOW(), NOW()
FROM sale_contract c JOIN sale_payment_plan p ON p.contract_id = c.id AND p.seq = 3
WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';

-- 按揭（已放款 DISBURSE）
INSERT INTO sale_mortgage (contract_id, bank_name, mortgage_amount, stage, disbursed_at, created_at, updated_at)
SELECT c.id, '工商银行杭州分行', 2777040.00, 'DISBURSE', CURDATE(), NOW(), NOW()
FROM sale_contract c WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';

-- 退房申请（DRAFT，待提交审批）
INSERT INTO sale_refund_exchange (refund_no, project_id, contract_id, room_id, type, refund_amount, reason, reason_category, status, created_at, updated_at)
SELECT 'TH-PJ2026001-2026-0001', 1, c.id, r.id, 'REFUND', 50000.00, '客户资金周转困难，申请退房', '资金困难', 'DRAFT', NOW(), NOW()
FROM sale_contract c JOIN mdm_room r ON r.id = c.room_id
WHERE c.contract_no = 'XHT-PJ2026001-2026-0001';

-- 编码计数器（按已造单量初始化，避免新单撞号）
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('RG',  DATE_FORMAT(CURDATE(), '%Y'), 1),
('XHT', DATE_FORMAT(CURDATE(), '%Y'), 1),
('SH',  DATE_FORMAT(CURDATE(), '%Y'), 2),
('TH',  DATE_FORMAT(CURDATE(), '%Y'), 1),
('ZK',  DATE_FORMAT(CURDATE(), '%Y'), 2),
('LF',  DATE_FORMAT(CURDATE(), '%Y'), 3)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));
