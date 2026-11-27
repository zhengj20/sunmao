-- =====================================================
-- V6 招采管理：需求→计划→立项→入围→发标→回标→评标→定标→直接委托/集采
-- =====================================================

CREATE TABLE proc_requirement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  req_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  account_id BIGINT COMMENT '成本科目',
  category_id BIGINT COMMENT '采购品类',
  plan_item_id BIGINT COMMENT '来源合约规划明细(余量校验)',
  description VARCHAR(1000),
  estimate_amount DECIMAL(16,2) COMMENT '预估金额',
  expect_date DATE COMMENT '期望到货/开工时间',
  urgency VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT',
  covered_by_framework TINYINT DEFAULT 0 COMMENT '是否已有集采覆盖',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/MERGED已并入计划/REJECTED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_req_code (req_code),
  KEY idx_req_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购需求';

CREATE TABLE proc_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL,
  plan_month VARCHAR(7) COMMENT 'yyyy-MM',
  name VARCHAR(128),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_pp_code (plan_code),
  KEY idx_pp_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购计划(月度汇总)';

CREATE TABLE proc_plan_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  requirement_id BIGINT,
  title VARCHAR(255),
  estimate_amount DECIMAL(16,2),
  expect_date DATE,
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ppi_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购计划明细';

CREATE TABLE proc_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_code VARCHAR(64) NOT NULL,
  project_id BIGINT NOT NULL COMMENT '业务项目',
  title VARCHAR(255) NOT NULL COMMENT '标的名称',
  budget_amount DECIMAL(16,2) COMMENT '预算(来源目标成本/合约规划)',
  plan_item_id BIGINT,
  fund_source VARCHAR(64) COMMENT '资金来源',
  procure_method VARCHAR(32) COMMENT 'PUBLIC_BID/INVITE_BID/COMPETITIVE/INQUIRY/DIRECT/FRAMEWORK',
  method_reason VARCHAR(255) COMMENT '方式选择理由(越级必填)',
  period_days INT COMMENT '工期',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/ENTRY入围中/TENDERING发标中/EVALUATING评标中/AWARDED已定标/CLOSED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_pp_code (project_code),
  KEY idx_pproj_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招采立项';

CREATE TABLE proc_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  proc_project_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  supplier_name VARCHAR(128),
  precheck_result VARCHAR(16) COMMENT 'PASSED/FAILED资格预审',
  precheck_remark VARCHAR(255),
  inspection_score DECIMAL(6,2) COMMENT '考察评分',
  inspection_conclusion VARCHAR(16) COMMENT 'GOOD/PASS/BAD',
  is_candidate TINYINT DEFAULT 1 COMMENT '1入围 0备选',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVING/APPROVED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_entry_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入围名单(含考察)';

CREATE TABLE proc_tender (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tender_code VARCHAR(64) NOT NULL,
  proc_project_id BIGINT NOT NULL,
  doc_version VARCHAR(16) COMMENT '标书版本',
  deposit_amount DECIMAL(16,2) COMMENT '保证金',
  deposit_refunded TINYINT DEFAULT 0,
  publish_time DATETIME COMMENT '发标时间',
  deadline DATETIME COMMENT '回标截止',
  clarify_notes JSON COMMENT '答疑纪要',
  status VARCHAR(16) DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED发标中/CLARIFY答疑/OPENED已开标/CLOSED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_tender_code (tender_code),
  KEY idx_tender_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发标管理';

CREATE TABLE proc_bid (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tender_id BIGINT NOT NULL,
  proc_project_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  supplier_name VARCHAR(128),
  bid_time DATETIME COMMENT '回标时间',
  tech_score DECIMAL(6,2) COMMENT '技术标得分',
  price_amount DECIMAL(16,2) COMMENT '商务报价',
  bid_ip VARCHAR(64) COMMENT '回标IP(围串标预警)',
  compliance_result VARCHAR(16) COMMENT '符合性 PASSED/FAILED',
  deviation_notes VARCHAR(500) COMMENT '偏差说明',
  unbalanced_flag TINYINT DEFAULT 0 COMMENT 'AI标记不平衡报价',
  bid_status VARCHAR(16) DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/OPENED/WINNER/LOSER/INVALID废标',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_bid_tender (tender_id), KEY idx_bid_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回标记录(技术/商务分离)';

CREATE TABLE proc_evaluation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  proc_project_id BIGINT NOT NULL,
  method VARCHAR(32) COMMENT 'COMPREHENSIVE综合评估/LOWEST最低价/COST_PERF性价比',
  tech_weight DECIMAL(5,2) DEFAULT 60 COMMENT '技术权重%',
  biz_weight DECIMAL(5,2) DEFAULT 40,
  expert_names VARCHAR(500) COMMENT '评标专家',
  status VARCHAR(16) DEFAULT 'SCORING' COMMENT 'SCORING/REPORTED评标报告已出',
  conclusion VARCHAR(1000),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_eval_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评标';

CREATE TABLE proc_eval_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  evaluation_id BIGINT NOT NULL,
  bid_id BIGINT NOT NULL,
  supplier_name VARCHAR(128),
  expert_name VARCHAR(64),
  tech_score DECIMAL(6,2),
  biz_score DECIMAL(6,2),
  total_score DECIMAL(6,2),
  rank_no INT,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_ed_eval (evaluation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家评分明细(留痕)';

CREATE TABLE proc_award (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  award_code VARCHAR(64) NOT NULL,
  proc_project_id BIGINT NOT NULL,
  winner_supplier_id BIGINT NOT NULL,
  winner_supplier_name VARCHAR(128),
  award_amount DECIMAL(16,2) NOT NULL COMMENT '定标价',
  budget_amount DECIMAL(16,2) COMMENT '预算对照',
  deviation DECIMAL(16,2) COMMENT '定标价-预算',
  deviation_pct DECIMAL(6,2),
  control_check VARCHAR(16) COMMENT 'PASSED余量内/WARN超余量预警/BLOCKED阻断',
  reason VARCHAR(500) COMMENT '推荐理由',
  notice_status VARCHAR(16) DEFAULT 'UNSENT' COMMENT 'UNSENT/SENT中标通知已发',
  contract_generated TINYINT DEFAULT 0 COMMENT '是否已生成合同草稿',
  contract_id BIGINT COMMENT '生成的合同',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_award_code (award_code),
  KEY idx_award_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定标';

CREATE TABLE proc_direct (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  supplier_id BIGINT NOT NULL,
  supplier_name VARCHAR(128),
  amount DECIMAL(16,2) NOT NULL,
  direct_reason VARCHAR(32) COMMENT '垄断/应急/续约/其他',
  reason_desc VARCHAR(500),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_direct_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直接委托';

CREATE TABLE proc_framework (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  framework_code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL COMMENT '战略集采协议',
  supplier_id BIGINT,
  supplier_name VARCHAR(128),
  category_id BIGINT,
  unit_price_table JSON COMMENT '框架单价表',
  valid_from DATE,
  valid_to DATE,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_fw_code (framework_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='战略集采协议';

CREATE TABLE proc_framework_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_code VARCHAR(64) NOT NULL,
  framework_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  content VARCHAR(500),
  quantity DECIMAL(14,3),
  amount DECIMAL(16,2),
  order_date DATE,
  status VARCHAR(16) DEFAULT 'ORDERED' COMMENT 'ORDERED/DELIVERED/CLOSED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_fwo_fw (framework_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集采下单';

CREATE TABLE proc_collusion_warn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  proc_project_id BIGINT,
  rule_code VARCHAR(32) COMMENT 'SAME_IP同IP/PRICE_LADDER规律梯度/SHAREHOLDER股东关联',
  detail VARCHAR(500),
  risk_level VARCHAR(16) DEFAULT 'MEDIUM',
  created_at DATETIME,
  KEY idx_cw_pp (proc_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='围串标预警';
