-- =====================================================
-- V76 数字员工工作流增强（借鉴成熟数字员工系统）
--   1) 环节接力：use_prev=1 时上游环节完整产物注入下游 Prompt
--   2) 人工审批门：requires_approval=1 的环节执行到此暂停，批准/驳回
--   3) 工作流定时：cron_expr + next_run_at（空=仅手动）
--   4) Token 消耗留痕：dig_task_run.token_in/token_out
--   5) 审批表 dig_wf_approval
-- =====================================================

-- ---------- 1. 工作流环节：接力 + 审批门 ----------
ALTER TABLE dig_workflow_step ADD COLUMN use_prev TINYINT DEFAULT 1 COMMENT '接力上一环节产物注入下游 1=是 0=否' AFTER on_fail;
ALTER TABLE dig_workflow_step ADD COLUMN requires_approval TINYINT DEFAULT 0 COMMENT '需人工审批(执行到此暂停) 1=是 0=否' AFTER use_prev;

-- ---------- 2. 工作流定时触发 ----------
ALTER TABLE dig_workflow ADD COLUMN cron_expr VARCHAR(64) COMMENT 'CRON(6段含秒),空=仅手动' AFTER description;
ALTER TABLE dig_workflow ADD COLUMN next_run_at DATETIME COMMENT '定时工作流下次执行时间' AFTER cron_expr;

-- ---------- 3. Token 消耗 ----------
ALTER TABLE dig_task_run ADD COLUMN token_in INT COMMENT 'LLM输入tokens' AFTER ai_mode;
ALTER TABLE dig_task_run ADD COLUMN token_out INT COMMENT 'LLM输出tokens' AFTER token_in;

-- ---------- 4. 工作流人工审批 ----------
CREATE TABLE dig_wf_approval (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  wf_run_id BIGINT NOT NULL COMMENT 'dig_workflow_run.id',
  step_order INT NOT NULL COMMENT '需审批的环节序号',
  step_name VARCHAR(255),
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  comment VARCHAR(512) COMMENT '审批意见',
  decided_by BIGINT,
  decided_at DATETIME,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_run (wf_run_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流人工审批';
