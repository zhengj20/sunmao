-- =====================================================
-- V58 知识库：知识图谱 + Wiki 关联
-- =====================================================

-- 文档关联表（知识图谱的边）
CREATE TABLE kb_link (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_doc_id BIGINT NOT NULL,
  target_doc_id BIGINT NOT NULL,
  relation_type VARCHAR(32) DEFAULT 'RELATED' COMMENT 'RELATED/REFERENCE/PREREQUISITE/CONTRAST',
  label VARCHAR(128) COMMENT '关系标签（显示用）',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT,
  created_at DATETIME,
  updated_by BIGINT,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_link (source_doc_id, target_doc_id),
  KEY idx_source (source_doc_id),
  KEY idx_target (target_doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档关联（知识图谱）';

-- 给 ai_kb_document 增加 wiki 字段
ALTER TABLE ai_kb_document ADD COLUMN wiki_path VARCHAR(255) COMMENT 'Wiki路径（如 /制度/折扣审批）';
ALTER TABLE ai_kb_document ADD COLUMN wiki_order INT DEFAULT 0 COMMENT 'Wiki排序';

-- 种子：文档间关联
INSERT INTO kb_link (source_doc_id, target_doc_id, relation_type, label, created_by, created_at, updated_at) VALUES
((SELECT id FROM ai_kb_document WHERE doc_code = 'KB_POLICY_001'),
 (SELECT id FROM ai_kb_document WHERE doc_code = 'KB_METRIC_001'),
 'REFERENCE', '回款率考核依据', 1, NOW(), NOW()),
((SELECT id FROM ai_kb_document WHERE doc_code = 'KB_POLICY_001'),
 (SELECT id FROM ai_kb_document WHERE doc_code = 'KB_CASE_001'),
 'REFERENCE', '关联案例', 1, NOW(), NOW()),
((SELECT id FROM ai_kb_document WHERE doc_code = 'KB_METRIC_002'),
 (SELECT id FROM ai_kb_document WHERE doc_code = 'KB_METRIC_001'),
 'RELATED', '同域指标', 1, NOW(), NOW()),
((SELECT id FROM ai_kb_document WHERE doc_code = 'KB_CASE_001'),
 (SELECT id FROM ai_kb_document WHERE doc_code = 'KB_POLICY_001'),
 'PREREQUISITE', '制度依据', 1, NOW(), NOW());

-- 种子：Wiki 路径
UPDATE ai_kb_document SET wiki_path = '/制度/折扣审批刚性制度', wiki_order = 1 WHERE doc_code = 'KB_POLICY_001';
UPDATE ai_kb_document SET wiki_path = '/指标/回款率口径', wiki_order = 1 WHERE doc_code = 'KB_METRIC_001';
UPDATE ai_kb_document SET wiki_path = '/指标/动态成本口径', wiki_order = 2 WHERE doc_code = 'KB_METRIC_002';
UPDATE ai_kb_document SET wiki_path = '/案例/供应商黑名单处置', wiki_order = 1 WHERE doc_code = 'KB_CASE_001';
