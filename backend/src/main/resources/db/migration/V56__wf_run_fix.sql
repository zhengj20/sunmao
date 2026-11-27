-- V56 补充 dig_workflow_run 缺失的 BaseEntity 字段
ALTER TABLE dig_workflow_run ADD COLUMN updated_by BIGINT AFTER updated_at;
ALTER TABLE dig_workflow_run ADD COLUMN version INT DEFAULT 0 AFTER updated_by;
ALTER TABLE dig_workflow_run ADD COLUMN deleted TINYINT DEFAULT 0 AFTER version;
