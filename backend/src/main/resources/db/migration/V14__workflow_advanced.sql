-- =====================================================
-- V14 工作流高级审批能力：抄送表
-- 说明：加签(前/后)、批量审批、字段级权限(fieldPerm)均复用现有 wfl_task /
--       wfl_template.definition(JSON) 承载，无需新表或新列，仅新增抄送表 wfl_cc。
-- =====================================================

CREATE TABLE wfl_cc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instance_id BIGINT NOT NULL COMMENT '流程实例ID',
  task_id BIGINT COMMENT '来源审批任务ID',
  cc_user_id BIGINT NOT NULL COMMENT '抄送接收人',
  cc_user_name VARCHAR(64) COMMENT '抄送接收人姓名',
  content VARCHAR(1000) COMMENT '抄送内容',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_cc_user (cc_user_id),
  KEY idx_cc_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批抄送(任务/审批动态抄送)';
