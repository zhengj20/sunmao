-- =====================================================
-- V29 补充协议表
-- =====================================================

CREATE TABLE cost_supplement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplement_code VARCHAR(64) NOT NULL,
  contract_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  amount DECIMAL(16,2) NOT NULL COMMENT '补充金额（可正可负）',
  supplement_date DATE,
  description VARCHAR(500),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_supplement_code (supplement_code),
  KEY idx_supplement_contract (contract_id),
  KEY idx_supplement_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同补充协议';
