-- =====================================================
-- V12 招采：零星采购(proc_misc + proc_misc_compare) + 审批流模板 + 比价家数参数
-- =====================================================

-- 1) 零星采购主表
CREATE TABLE proc_misc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL COMMENT '业务项目',
  title VARCHAR(255) NOT NULL COMMENT '采购事项标题',
  amount DECIMAL(18,2) COMMENT '采购金额',
  reason VARCHAR(500) COMMENT '采购事由',
  compare_attachment VARCHAR(512) COMMENT '比价附件(如三家报价单)',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_misc_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='零星采购';

-- 2) 零星采购比价明细（提交硬校验：家数≥阈值且各家报价一致）
CREATE TABLE proc_misc_compare (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  misc_id BIGINT NOT NULL COMMENT '所属零星采购',
  supplier_name VARCHAR(128) COMMENT '供应商名称',
  price DECIMAL(18,2) COMMENT '报价金额',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_mmc_misc (misc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='零星采购比价明细';

-- 3) 审批流模板：PROC_MISC（招采经理→集团审批）
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_PROC_MISC', '零星采购审批流', 'PROC_MISC',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 '零星采购须满足比价家数且报价一致方可提交');

-- 4) 系统参数：零星采购最少比价家数（默认3）
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('proc.misc.min_compare', '3', '零星采购最少比价家数', '提交审批硬校验：家数≥该值且各家报价一致');
