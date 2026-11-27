-- =====================================================
-- V78 数字员工：知识库内容绑定（员工级 + 工作流级）
-- 绑定的具体文档在任务执行时整篇注入提示词（与技能 kbSearch 关键词检索互补）
-- =====================================================

ALTER TABLE dig_employee
  ADD COLUMN kb_doc_ids VARCHAR(512) DEFAULT NULL COMMENT '绑定知识库文档ID（逗号分隔，执行时注入提示词）' AFTER model_config_id;

ALTER TABLE dig_workflow
  ADD COLUMN kb_doc_ids VARCHAR(512) DEFAULT NULL COMMENT '绑定知识库文档ID（逗号分隔，作用于全部环节）' AFTER next_run_at;
