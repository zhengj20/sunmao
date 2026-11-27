-- =====================================================
-- V5 成本管理：目标成本/合约规划/合同/变更/付款/动态成本/无效成本/指标库
-- =====================================================

CREATE TABLE cost_target_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  stage VARCHAR(32) COMMENT 'LAND拿地版/SCHEME方案版/CONSTRUCTION施工图版/BASE基准版',
  total_amount DECIMAL(16,2) COMMENT '目标总成本',
  build_area DECIMAL(14,2) COMMENT '计算单方用的建筑面积',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/LOCKED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_target_code (version_code),
  KEY idx_target_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标成本版本';

CREATE TABLE cost_target_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  account_code VARCHAR(64),
  account_name VARCHAR(128),
  quantity DECIMAL(14,3) COMMENT '工程量',
  unit VARCHAR(16),
  unit_price DECIMAL(14,2) COMMENT '单价',
  amount DECIMAL(16,2) COMMENT '金额=量×价',
  unit_cost DECIMAL(14,2) COMMENT '单方成本(金额/建筑面积)',
  indicator_ref VARCHAR(255) COMMENT '引用指标来源',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ti_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标成本明细';

CREATE TABLE cost_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  target_version_id BIGINT COMMENT '依据的目标成本版本',
  name VARCHAR(128),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_plan_code (plan_code),
  KEY idx_plan_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合约规划';

CREATE TABLE cost_plan_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  account_code VARCHAR(64),
  account_name VARCHAR(128),
  planned_amount DECIMAL(16,2) NOT NULL COMMENT '规划金额',
  contracted_amount DECIMAL(16,2) DEFAULT 0 COMMENT '已签约占用(合同回写)',
  pending_amount DECIMAL(16,2) DEFAULT 0 COMMENT '在途占用(审批中合同)',
  scope VARCHAR(255) COMMENT '范围描述',
  expected_sign_date DATE COMMENT '拟签时间',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_pi_plan (plan_id), KEY idx_pi_project_account (project_id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合约规划明细(科目-规划-合同三联穿透)';

CREATE TABLE cost_contract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  plan_item_id BIGINT COMMENT '合约规划明细',
  name VARCHAR(255) NOT NULL,
  supplier_id BIGINT,
  supplier_name VARCHAR(128),
  contract_type VARCHAR(32) COMMENT 'GC总包/ZC分包/CL材料/FW服务',
  signed_amount DECIMAL(16,2) NOT NULL COMMENT '签约金额',
  signed_date DATE,
  scope VARCHAR(500) COMMENT '承包范围',
  start_date DATE,
  end_date DATE,
  retention_rate DECIMAL(5,2) DEFAULT 3 COMMENT '质保金比例%',
  retention_released DECIMAL(16,2) DEFAULT 0 COMMENT '已释放质保金',
  source VARCHAR(32) DEFAULT 'MANUAL' COMMENT 'MANUAL手动/AWARD招采定标生成',
  award_id BIGINT COMMENT '来源定标单',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/EFFECTIVE生效/SETTLED结算/CLOSED',
  settled_amount DECIMAL(16,2) COMMENT '结算金额=签约+确认变更-扣款',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_contract_code (contract_code),
  KEY idx_contract_project (project_id), KEY idx_contract_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同台账';

CREATE TABLE cost_contract_alloc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  account_name VARCHAR(128),
  amount DECIMAL(16,2) NOT NULL COMMENT '分摊金额',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ca_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同金额分摊科目';

CREATE TABLE cost_change (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  change_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  contract_id BIGINT COMMENT '无合同变更挂科目',
  account_id BIGINT COMMENT '无合同变更挂科目',
  change_type VARCHAR(16) COMMENT 'SJBG设计变更/GCQZ工程签证/QSSY洽商',
  title VARCHAR(255) NOT NULL,
  reason_category VARCHAR(64) COMMENT '原因归类',
  description VARCHAR(1000),
  estimate_amount DECIMAL(16,2) COMMENT '估价金额(一阶段)',
  confirmed_amount DECIMAL(16,2) COMMENT '确认金额(二阶段)',
  deduct_amount DECIMAL(16,2) DEFAULT 0 COMMENT '扣款金额',
  status VARCHAR(16) DEFAULT 'ESTIMATING' COMMENT 'ESTIMATING估价中/ESTIMATE_APPROVING/CONFIRMING待确认/APPROVING确认审批中/CONFIRMED已确认/REJECTED',
  require_contract TINYINT DEFAULT 1 COMMENT '是否有合同',
  attachments JSON,
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_change_code (change_code),
  KEY idx_change_project (project_id), KEY idx_change_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更管理(两阶段:估价→确认)';

CREATE TABLE cost_payment_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  term_no INT,
  term_desc VARCHAR(255),
  plan_amount DECIMAL(16,2),
  plan_date DATE,
  paid_amount DECIMAL(16,2) DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_pp_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款计划(合同条款生成)';

CREATE TABLE cost_payment_apply (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  payment_type VARCHAR(16) COMMENT 'PREPAY预付/PROGRESS进度/SETTLE结算/RETAIN质保',
  apply_amount DECIMAL(16,2) NOT NULL,
  applied_amount DECIMAL(16,2) DEFAULT 0 COMMENT '累计已付(申请时点)',
  invoice_amount DECIMAL(16,2) DEFAULT 0 COMMENT '本次关联发票额',
  image_progress_id BIGINT COMMENT '形象进度确认单(二期)',
  image_progress_pct DECIMAL(5,2) COMMENT '形象进度%',
  description VARCHAR(500),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/PAID/REJECTED',
  approved_amount DECIMAL(16,2) COMMENT '审批通过金额',
  paid_amount DECIMAL(16,2) DEFAULT 0 COMMENT '实付金额',
  paid_date DATE,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_payment_code (payment_code),
  KEY idx_pay_project (project_id), KEY idx_pay_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款申请';

CREATE TABLE cost_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT,
  contract_id BIGINT,
  payment_id BIGINT COMMENT '关联付款单',
  supplier_id BIGINT,
  invoice_no VARCHAR(64) NOT NULL,
  invoice_type VARCHAR(16) COMMENT '增值税专用/普通',
  amount DECIMAL(16,2) NOT NULL COMMENT '价税合计',
  tax_amount DECIMAL(16,2),
  invoice_date DATE,
  check_status VARCHAR(16) DEFAULT 'UNCHECKED' COMMENT 'UNCHECKED/PASSED/FAILED查验状态',
  duplicate_hash VARCHAR(64) COMMENT '查重hash(全平台发票池)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_invoice_no (invoice_no),
  KEY idx_invoice_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收票登记(发票池)';

CREATE TABLE cost_dynamic_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  snap_month VARCHAR(7) NOT NULL COMMENT 'yyyy-MM',
  target_total DECIMAL(16,2) COMMENT '目标成本',
  contracted_total DECIMAL(16,2) COMMENT '已签合同',
  confirmed_change DECIMAL(16,2) COMMENT '已确认变更',
  estimated_change DECIMAL(16,2) COMMENT '预估变更',
  non_contract DECIMAL(16,2) COMMENT '非合约支出',
  remaining DECIMAL(16,2) COMMENT '余量',
  dynamic_total DECIMAL(16,2) COMMENT '动态成本合计',
  detail_json JSON COMMENT '科目级构成',
  created_at DATETIME,
  UNIQUE KEY uk_snap (project_id, snap_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态成本月度快照(事件驱动重算,不可修改)';

CREATE TABLE cost_invalid (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  invalid_type VARCHAR(32) COMMENT '超合同支付/返工损失/赔偿/罚款/其他',
  amount DECIMAL(16,2) NOT NULL,
  occurred_date DATE,
  contract_id BIGINT,
  responsible_dept VARCHAR(128) COMMENT '责任部门',
  description VARCHAR(500),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/CONFIRMED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_invalid_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无效成本认定台账';

CREATE TABLE cost_indicator (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT COMMENT 'NULL=集团指标库',
  account_code VARCHAR(64) COMMENT '科目编码',
  product_category VARCHAR(32) COMMENT '业态',
  indicator_type VARCHAR(16) COMMENT 'COST单方/CONTENT含量',
  indicator_name VARCHAR(128) COMMENT '指标名,如 钢筋含量',
  unit VARCHAR(16) COMMENT '元/㎡ 或 kg/㎡',
  value DECIMAL(14,3),
  source VARCHAR(128) COMMENT '来源项目/版本',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ind_account (account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本指标库';
