-- =====================================================
-- V3 工作流引擎：模板/实例/任务/委托/超时
-- =====================================================

CREATE TABLE wfl_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  biz_type VARCHAR(64) NOT NULL COMMENT '绑定的单据类型，如 COST_CONTRACT',
  tpl_version INT DEFAULT 1 COMMENT '业务版本(在途走旧版)',
  definition JSON NOT NULL COMMENT '节点定义JSON',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_tpl_biz_active (biz_type, status),
  KEY idx_tpl_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板(多版本,在途走旧版)';

CREATE TABLE wfl_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL,
  template_code VARCHAR(64),
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT NOT NULL,
  biz_title VARCHAR(255) COMMENT '单据标题(待办展示)',
  biz_amount DECIMAL(16,2) COMMENT '单据金额(条件路由用,提交时点快照)',
  snapshot JSON COMMENT '业务快照(摘要展示)',
  current_node_id VARCHAR(64),
  inflight_flag TINYINT DEFAULT 1 COMMENT '在途标记(1在途,结束置0)：配合唯一约束保证同单同刻仅一在途实例',
  status VARCHAR(16) DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED/WITHDRAWN',
  submitter_id BIGINT,
  submitter_name VARCHAR(64),
  submitted_at DATETIME,
  finished_at DATETIME,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_biz_inflight (biz_type, biz_id, inflight_flag),
  KEY idx_inst_submitter (submitter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例(同一单据同刻仅一在途实例,唯一约束+状态机保证)';

CREATE TABLE wfl_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  node_id VARCHAR(64) NOT NULL,
  node_name VARCHAR(64),
  assignee_id BIGINT NOT NULL,
  assignee_name VARCHAR(64),
  agent_id BIGINT COMMENT '受托代理人',
  agent_name VARCHAR(64),
  countersign VARCHAR(16) DEFAULT 'ANY' COMMENT 'ANY或签/ALL会签',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELLED',
  comment VARCHAR(500),
  attachments JSON,
  arrived_at DATETIME,
  acted_at DATETIME,
  duration_minutes BIGINT,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_task_assignee (assignee_id, status),
  KEY idx_task_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

CREATE TABLE wfl_entrust (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  from_user_name VARCHAR(64),
  to_user_id BIGINT NOT NULL,
  to_user_name VARCHAR(64),
  biz_scope VARCHAR(64) COMMENT 'ALL或指定biz_type',
  start_date DATE,
  end_date DATE,
  reason VARCHAR(255),
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委托代理(休假出差,审批留痕代XX审批)';

CREATE TABLE wfl_timeout_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT,
  instance_id BIGINT,
  node_name VARCHAR(64),
  assignee_id BIGINT,
  timeout_hours INT,
  action_taken VARCHAR(64) COMMENT 'REMIND/ESCALATE',
  created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超时记录';

-- ========== 种子：一期各单据默认审批流(三级审批:部门→分管→集团) ==========
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_COST_TARGET',  '目标成本审批流',   'COST_TARGET',  '{"nodes":[{"id":"n1","type":"APPROVE","name":"区域成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"CONDITION","name":"金额路由","branches":[{"when":{"field":"amount","op":">=","value":10000000},"next":"n3"},{"next":"n4"}]},{"id":"n3","type":"APPROVE","name":"集团成本审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}', '≥1000万上集团会签'),
('TPL_COST_PLAN',    '合约规划审批流',   'COST_PLAN',    '{"nodes":[{"id":"n1","type":"APPROVE","name":"区域成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团成本审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_COST_CONTRACT','合同评审审批流',   'COST_CONTRACT','{"nodes":[{"id":"n1","type":"APPROVE","name":"区域成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"CONDITION","name":"金额路由","branches":[{"when":{"field":"amount","op":">=","value":5000000},"next":"n3"},{"next":"n4"}]},{"id":"n3","type":"APPROVE","name":"集团成本审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}', '≥500万上集团会签'),
('TPL_COST_CHANGE',  '变更确认审批流',   'COST_CHANGE',  '{"nodes":[{"id":"n1","type":"APPROVE","name":"区域成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团成本审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_COST_PAYMENT', '付款审批流',       'COST_PAYMENT', '{"nodes":[{"id":"n1","type":"APPROVE","name":"财务经理","assigneeType":"ROLE","assignees":["FIN_MGR"],"countersign":"ANY"},{"id":"n2","type":"CONDITION","name":"金额路由","branches":[{"when":{"field":"amount","op":">=","value":1000000},"next":"n3"},{"next":"n4"}]},{"id":"n3","type":"APPROVE","name":"集团财务审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}', '≥100万上集团会签'),
('TPL_PROC_REQ',     '采购需求审批流',   'PROC_REQUIREMENT', '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_PROC_PROJECT', '招采立项审批流',   'PROC_PROJECT', '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_PROC_ENTRY',   '入围名单审批流',   'PROC_ENTRY',   '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_PROC_AWARD',   '定标审批流',       'PROC_AWARD',   '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n3","type":"APPROVE","name":"集团定标审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}', NULL),
('TPL_PROC_DIRECT',  '直接委托审批流',   'PROC_DIRECT',  '{"nodes":[{"id":"n1","type":"APPROVE","name":"招采经理","assigneeType":"ROLE","assignees":["PROC_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团审批(直接委托必上)","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}', '越级方式必须集团审批'),
('TPL_INVALID_COST', '无效成本认定流',   'COST_INVALID', '{"nodes":[{"id":"n1","type":"APPROVE","name":"成本经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"END","name":"结束"}],"start":"n1"}', NULL);
