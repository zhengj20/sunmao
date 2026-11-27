-- =====================================================
-- V55 数字员工工作流编排
-- =====================================================

CREATE TABLE dig_workflow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  wf_code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_by BIGINT,
  created_at DATETIME,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_wf_code (wf_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dig_workflow_step (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  wf_id BIGINT NOT NULL,
  step_order INT NOT NULL,
  task_id BIGINT NOT NULL,
  name VARCHAR(255),
  condition_expr TEXT,
  on_fail VARCHAR(16) DEFAULT 'STOP',
  timeout_sec INT DEFAULT 120,
  created_at DATETIME,
  updated_at DATETIME,
  deleted TINYINT DEFAULT 0,
  KEY idx_wf (wf_id),
  UNIQUE KEY uk_wf_order (wf_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dig_workflow_run (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  wf_id BIGINT NOT NULL,
  wf_name VARCHAR(255),
  status VARCHAR(16) DEFAULT 'RUNNING',
  current_step INT DEFAULT 0,
  total_steps INT DEFAULT 0,
  started_at DATETIME,
  finished_at DATETIME,
  steps JSON,
  output TEXT,
  error TEXT,
  created_by BIGINT,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_wf (wf_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：月度经营分析工作流
INSERT INTO dig_workflow (wf_code, name, description, status, created_by, created_at, updated_at) VALUES
('WF-MONTHLY-ANALYSIS', '月度经营分析', '成本偏差 → 回款跟踪 → 合同风险 → 招采巡检', 'ACTIVE', 1, NOW(), NOW());

INSERT INTO dig_workflow_step (wf_id, step_order, task_id, name, on_fail, created_at, updated_at) VALUES
((SELECT id FROM dig_workflow WHERE wf_code = 'WF-MONTHLY-ANALYSIS'), 1,
 (SELECT id FROM dig_task WHERE task_code = 'TASK-COST-DEV'), '成本偏差分析', 'CONTINUE', NOW(), NOW()),
((SELECT id FROM dig_workflow WHERE wf_code = 'WF-MONTHLY-ANALYSIS'), 2,
 (SELECT id FROM dig_task WHERE task_code = 'TASK-FIN-RECEIPT'), '回款跟踪分析', 'CONTINUE', NOW(), NOW()),
((SELECT id FROM dig_workflow WHERE wf_code = 'WF-MONTHLY-ANALYSIS'), 3,
 (SELECT id FROM dig_task WHERE task_code = 'TASK-LEGAL-CONTRACT'), '合同风险审查', 'STOP', NOW(), NOW()),
((SELECT id FROM dig_workflow WHERE wf_code = 'WF-MONTHLY-ANALYSIS'), 4,
 (SELECT id FROM dig_task WHERE task_code = 'TASK-PROC-LEDGER'), '招采台账巡检', 'STOP', NOW(), NOW());

-- 菜单
INSERT INTO sys_menu (parent_id, name, type, path, component, perm_code, icon, sort, visible, created_at, updated_at) VALUES
(500, '工作流编排', 'M', '/digital/workflow', 'digital/workflow/index', NULL, 'Connection', 4, 1, NOW(), NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, (SELECT id FROM sys_menu WHERE path = '/digital/workflow')
FROM sys_role_menu rm WHERE rm.menu_id = 507 GROUP BY rm.role_id;
