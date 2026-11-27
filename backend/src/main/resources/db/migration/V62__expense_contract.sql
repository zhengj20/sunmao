-- =====================================================
-- V62 费用管理：费用合同（合同台账 + 付款跟踪）
--   费用类合同登记（供应商/金额/起止期/状态）；
--   付款单可选关联合同，实付登记时原子回写合同已付金额（超合同额阻断）
-- =====================================================

-- 1) 费用合同台账
CREATE TABLE exp_contract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',
  name VARCHAR(200) NOT NULL COMMENT '合同名称',
  supplier VARCHAR(128) COMMENT '供应商/对方单位',
  amount DECIMAL(16,2) NOT NULL COMMENT '合同金额',
  paid_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已付金额(实付回写)',
  sign_date DATE COMMENT '签订日期',
  start_date DATE COMMENT '生效日期',
  end_date DATE COMMENT '到期日期',
  status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/TERMINATED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_contract_no (contract_no),
  KEY idx_contract_status (status), KEY idx_contract_supplier (supplier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用合同台账(付款跟踪)';

-- 2) 付款单可选关联费用合同（付款跟踪）
ALTER TABLE exp_payment ADD COLUMN contract_id BIGINT COMMENT '关联费用合同(可选)' AFTER reimb_id;
ALTER TABLE exp_payment ADD KEY idx_pay_contract (contract_id);

-- 3) 编码规则
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('FKH', '费用合同', 'FKH-{YYYY}-{seq:4}', 'FKH-2026-0001');

-- 4) 菜单 + 角色授权（1集团管理员 / 2成本经理 / 4财务经理）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(128, 6, '费用合同', 'M', '/expense/contract', 'expense/contract/index', '', 2);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 128), (2, 128), (4, 128);
