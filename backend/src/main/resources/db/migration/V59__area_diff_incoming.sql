-- =====================================================
-- V59 销售补全：
--   1) 面积补差（多退少补）：合同面积 vs 实测面积 → 差价款 → 少补收款/多退退款
--   2) 来账（银企直联 / 手机短信）：收款信息登记 → 智能/手动匹配到合同·房间 → 核销生成实收
-- 约定：金额 DECIMAL(16,2)；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- =====================================================

-- 1) 面积补差（多退少补）
CREATE TABLE sale_area_diff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  diff_no VARCHAR(64) NOT NULL COMMENT '补差单号',
  project_id BIGINT NOT NULL,
  contract_id BIGINT COMMENT '关联合同',
  room_id BIGINT COMMENT '关联房间',
  room_code VARCHAR(64) COMMENT '房号(冗余)',
  customer_id BIGINT COMMENT '客户',
  customer_name VARCHAR(64) COMMENT '客户名(冗余)',
  contract_area DECIMAL(14,2) COMMENT '合同面积㎡',
  measured_area DECIMAL(14,2) COMMENT '实测面积㎡',
  diff_area DECIMAL(14,2) COMMENT '面积差㎡(实测-合同)',
  unit_price DECIMAL(16,2) COMMENT '补差单价(元/㎡)',
  diff_amount DECIMAL(16,2) COMMENT '差价款(正=少补收款, 负=多退退款)',
  direction VARCHAR(16) COMMENT 'COLLECT少补/REFUND多退/NONE无差异',
  threshold DECIMAL(5,4) DEFAULT 0.0300 COMMENT '误差免赔线(默认3%)',
  within_tolerance TINYINT DEFAULT 0 COMMENT '是否3%误差范围内(仅提示)',
  source VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL手工/IMPORT导入',
  batch_no VARCHAR(64) COMMENT '导入批次号',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING待结算/GENERATED已生成/SETTLED已结清',
  settle_amount DECIMAL(16,2) COMMENT '已结算金额',
  plan_id BIGINT COMMENT '少补收款生成的应收计划ID',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_diff_no (diff_no),
  KEY idx_diff_project (project_id),
  KEY idx_diff_contract (contract_id),
  KEY idx_diff_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面积补差(多退少补)';

-- 2) 来账（银企直联 / 手机短信）
CREATE TABLE sale_incoming (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  incoming_no VARCHAR(64) NOT NULL COMMENT '来账单号',
  project_id BIGINT NOT NULL,
  source VARCHAR(16) COMMENT 'BANK银企直联/SMS手机短信',
  payer_name VARCHAR(64) COMMENT '付款人(客户名)',
  payer_account VARCHAR(64) COMMENT '付款账号',
  amount DECIMAL(16,2) NOT NULL COMMENT '来账金额',
  bank_serial VARCHAR(64) COMMENT '银行流水号/短信流水号',
  received_at DATETIME COMMENT '到账时间',
  contract_id BIGINT COMMENT '匹配到的合同',
  room_id BIGINT COMMENT '匹配到的房间',
  room_code VARCHAR(64) COMMENT '匹配到的房号(冗余)',
  match_status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING待匹配/MATCHED已匹配/REJECTED已排除',
  receipt_id BIGINT COMMENT '生成的首笔实收单',
  match_by BIGINT COMMENT '匹配人',
  match_at DATETIME COMMENT '匹配时间',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_incoming_no (incoming_no),
  KEY idx_inc_project (project_id, match_status),
  KEY idx_inc_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来账(银企直联/手机短信)';

-- =====================================================
-- 编码规则 + 计数器
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('MC', '面积补差单', 'MC-{projectCode}-{YYYY}-{seq:4}', 'MC-PJ2026001-2026-0001'),
('LZ', '来账单',     'LZ-{projectCode}-{YYYY}-{seq:4}', 'LZ-PJ2026001-2026-0001');

-- =====================================================
-- RBAC：菜单（面积补差 125，销售管理 id=11 下）+ 排序 + 授权
-- 来账对账作为「回款管理(115)」内的 Tab，不再单列菜单
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(125, 11, '面积补差', 'M', '/sales/area-diff', 'sales/area-diff/index', 'ScaleToOriginal', 14);

UPDATE sys_menu SET sort = 15 WHERE id = 116;   -- 退换管理
UPDATE sys_menu SET sort = 16 WHERE id = 117;   -- 销售报表

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 125), (6, 125);

-- =====================================================
-- 种子数据（项目1，基于已签约合同 1#-002）
-- =====================================================
-- 面积补差：一少补(COLLECT,实测+5%)、一多退(REFUND,实测-6%)
INSERT INTO sale_area_diff (diff_no, project_id, contract_id, room_id, room_code, customer_id, customer_name,
  contract_area, measured_area, diff_area, unit_price, diff_amount, direction, threshold, within_tolerance,
  source, status, settle_amount, created_at, updated_at)
SELECT 'MC-PJ2026001-2026-0001', 1, c.id, c.room_id, r.room_code, c.customer_id, cu.name,
  r.area, ROUND(r.area*1.05,2), ROUND(r.area*0.05,2), ROUND(c.contract_price/r.area,2),
  ROUND(ROUND(r.area*0.05,2) * ROUND(c.contract_price/r.area,2),2), 'COLLECT', 0.0300, 0,
  'IMPORT', 'PENDING', 0.00, NOW(), NOW()
FROM sale_contract c JOIN mdm_room r ON r.id=c.room_id
LEFT JOIN mdm_customer cu ON cu.id=c.customer_id
WHERE c.contract_no='XHT-PJ2026001-2026-0001' AND c.deleted=0;

INSERT INTO sale_area_diff (diff_no, project_id, contract_id, room_id, room_code, customer_id, customer_name,
  contract_area, measured_area, diff_area, unit_price, diff_amount, direction, threshold, within_tolerance,
  source, status, settle_amount, created_at, updated_at)
SELECT 'MC-PJ2026001-2026-0002', 1, c.id, c.room_id, r.room_code, c.customer_id, cu.name,
  r.area, ROUND(r.area*0.94,2), ROUND(-r.area*0.06,2), ROUND(c.contract_price/r.area,2),
  ROUND(ROUND(-r.area*0.06,2) * ROUND(c.contract_price/r.area,2),2), 'REFUND', 0.0300, 0,
  'IMPORT', 'PENDING', 0.00, NOW(), NOW()
FROM sale_contract c JOIN mdm_room r ON r.id=c.room_id
LEFT JOIN mdm_customer cu ON cu.id=c.customer_id
WHERE c.contract_no='XHT-PJ2026001-2026-0001' AND c.deleted=0;

-- 来账：银企直联 1 笔（对应未收按揭款）+ 手机短信 1 笔（对应尾款）
INSERT INTO sale_incoming (incoming_no, project_id, source, payer_name, payer_account, amount, bank_serial, received_at, match_status, remark, created_at, updated_at)
SELECT 'LZ-PJ2026001-2026-0001', 1, 'BANK', cu.name, '62220001234', 2777040.00, 'BANK20260907-0001', NOW(), 'PENDING', '银企直联来账(按揭款)', NOW(), NOW()
FROM sale_contract c LEFT JOIN mdm_customer cu ON cu.id=c.customer_id
WHERE c.contract_no='XHT-PJ2026001-2026-0001' AND c.deleted=0;

INSERT INTO sale_incoming (incoming_no, project_id, source, payer_name, payer_account, amount, bank_serial, received_at, match_status, remark, created_at, updated_at)
SELECT 'LZ-PJ2026001-2026-0002', 1, 'SMS', cu.name, '62220005678', 100000.00, 'SMS20260907-0002', NOW(), 'PENDING', '手机短信收款(部分)', NOW(), NOW()
FROM sale_contract c LEFT JOIN mdm_customer cu ON cu.id=c.customer_id
WHERE c.contract_no='XHT-PJ2026001-2026-0001' AND c.deleted=0;

-- 编码计数器（按已造单量初始化）
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('MC', DATE_FORMAT(CURDATE(), '%Y'), 2),
('LZ', DATE_FORMAT(CURDATE(), '%Y'), 2)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));
