-- V57 补充工作流三张表缺失的 BaseEntity 字段（tenant_id / updated_by）
ALTER TABLE dig_workflow ADD COLUMN tenant_id BIGINT AFTER id;
ALTER TABLE dig_workflow ADD COLUMN updated_by BIGINT AFTER updated_at;

ALTER TABLE dig_workflow_step ADD COLUMN tenant_id BIGINT AFTER id;
ALTER TABLE dig_workflow_step ADD COLUMN created_by BIGINT AFTER updated_at;
ALTER TABLE dig_workflow_step ADD COLUMN updated_by BIGINT AFTER created_by;
ALTER TABLE dig_workflow_step ADD COLUMN version INT DEFAULT 0 AFTER updated_by;

ALTER TABLE dig_workflow_run ADD COLUMN tenant_id BIGINT AFTER id;
