-- =====================================================
-- V75 数字员工模块完善
--   1) dig_workflow_run 增加 failed_steps：失败步骤数（含超时），0=全部成功
-- =====================================================
ALTER TABLE dig_workflow_run ADD COLUMN failed_steps INT DEFAULT 0 COMMENT '失败(含超时)步骤数,0=全部成功' AFTER total_steps;
