-- =====================================================
-- V7 投资货值 + 资金管理(轻量)
-- =====================================================

CREATE TABLE inv_calc_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  stage VARCHAR(32) COMMENT 'LAND拿地测算/UPDATE版',
  sale_value DECIMAL(16,2) COMMENT '总货值',
  total_cost DECIMAL(16,2) COMMENT '总成本(土地+开发)',
  three_fee DECIMAL(16,2) COMMENT '三费',
  tax_amount DECIMAL(16,2) COMMENT '税金',
  net_profit DECIMAL(16,2) COMMENT '净利润',
  profit_rate DECIMAL(6,2) COMMENT '净利率%',
  irr DECIMAL(6,2) COMMENT 'IRR%',
  payback_years DECIMAL(6,2) COMMENT '回收期(年)',
  detail_json JSON COMMENT '测算明细',
  decision_notes VARCHAR(1000) COMMENT '投资决策会记录',
  locked TINYINT DEFAULT 0 COMMENT '基线锁定(后评估基准)',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/LOCKED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_calc_code (version_code),
  KEY idx_calc_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资测算版本';

CREATE TABLE inv_value (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  product_category VARCHAR(32) COMMENT '业态',
  area DECIMAL(14,2) COMMENT '面积',
  price_version VARCHAR(32) COMMENT '预测价/备案价/成交价',
  price DECIMAL(14,2) COMMENT '单价',
  total_value DECIMAL(16,2) COMMENT '货值=面积×价格',
  supply_date DATE COMMENT '供货时间',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_iv_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货值台账';

CREATE TABLE inv_value_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  flow_month VARCHAR(7) COMMENT 'yyyy-MM',
  sold_value DECIMAL(16,2) DEFAULT 0 COMMENT '已售(签约)',
  refund_value DECIMAL(16,2) DEFAULT 0 COMMENT '退房回流',
  supply_value DECIMAL(16,2) DEFAULT 0 COMMENT '新增供货',
  stock_value DECIMAL(16,2) DEFAULT 0 COMMENT '期末存货',
  created_at DATETIME,
  UNIQUE KEY uk_ivf (project_id, flow_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货值供销存(消费sales事件,一期为演示数据)';

CREATE TABLE inv_dynamic_profit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  snap_month VARCHAR(7) NOT NULL,
  dynamic_value DECIMAL(16,2) COMMENT '动态货值',
  dynamic_cost DECIMAL(16,2) COMMENT '动态成本(消费cost事件)',
  three_fee DECIMAL(16,2) COMMENT '动态三费',
  tax_amount DECIMAL(16,2),
  dynamic_profit DECIMAL(16,2) COMMENT '动态利润=货值-成本-三费-税金',
  profit_rate DECIMAL(6,2),
  baseline_profit DECIMAL(16,2) COMMENT '拿地测算利润(对照)',
  detail_json JSON,
  created_at DATETIME,
  UNIQUE KEY uk_idp (project_id, snap_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态利润月度快照(不可修改)';

CREATE TABLE fund_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_month VARCHAR(7) NOT NULL,
  project_id BIGINT COMMENT 'NULL=集团汇总',
  version_type VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT初稿/FINAL定稿',
  receipt_plan DECIMAL(16,2) DEFAULT 0 COMMENT '计划收款(销售应收+按揭)',
  payment_plan DECIMAL(16,2) DEFAULT 0 COMMENT '计划付款(成本+费用+税费)',
  net_plan DECIMAL(16,2) COMMENT '净流入计划',
  receipt_actual DECIMAL(16,2) DEFAULT 0,
  payment_actual DECIMAL(16,2) DEFAULT 0,
  gap_amount DECIMAL(16,2) COMMENT '资金缺口',
  detail_json JSON COMMENT '明细构成',
  created_at DATETIME,
  UNIQUE KEY uk_fp (plan_month, project_id, version_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度资金计划(自动汇总)';

CREATE TABLE fund_finance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT,
  finance_type VARCHAR(32) COMMENT '开发贷/信托/债券',
  bank_name VARCHAR(128),
  amount DECIMAL(16,2) COMMENT '额度',
  used_amount DECIMAL(16,2) DEFAULT 0,
  rate DECIMAL(6,3) COMMENT '利率%',
  start_date DATE,
  due_date DATE,
  repay_plan JSON COMMENT '还款计划',
  status VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/REPAID/OVERDUE',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ff_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='融资台账';
