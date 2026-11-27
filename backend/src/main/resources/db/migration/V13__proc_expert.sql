-- =====================================================
-- V13 招采：评标专家库(proc_expert) + 编码规则补充(计划/框架/集采订单)
-- =====================================================

-- 1) 评标专家库（抽取回避：ACTIVE 且 avoid_orgs 不含回避关键词）
CREATE TABLE proc_expert (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '专家姓名',
  phone VARCHAR(32) COMMENT '联系电话',
  specialty VARCHAR(128) COMMENT '专业领域',
  avoid_orgs VARCHAR(255) COMMENT '回避单位(逗号分隔,抽取时排除含关键词)',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_expert_specialty (specialty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评标专家库';

-- 2) 编码规则补充（编码一致性：ProcPlanService/ProcFrameworkService 原用时间戳拼号，改用编码引擎）
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('JH', '采购计划', 'JH-{YYYY}{MM}-{seq:4}', 'JH-202609-0001'),
('FW', '战略集采协议', 'FW-{YYYY}{MM}-{seq:4}', 'FW-202609-0001'),
('DD', '集采订单', 'DD-{YYYY}{MM}-{seq:4}', 'DD-202609-0001');
