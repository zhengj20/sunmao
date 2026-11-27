-- 合同模板（模板文件 + 占位符，用于"根据模板生成合同"与在线预览）
CREATE TABLE cost_contract_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL COMMENT '模板名称',
  contract_type VARCHAR(32) COMMENT '适用合同类型 GC总包/ZC分包/CL材料/FW服务',
  file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
  file_path VARCHAR(1000) NOT NULL COMMENT '存储相对路径（相对 uploads 目录）',
  file_size BIGINT COMMENT '文件大小(bytes)',
  placeholders TEXT COMMENT '占位符说明 JSON',
  description VARCHAR(500) COMMENT '描述',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_tpl_type (contract_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同模板';
