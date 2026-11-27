-- =====================================================
-- V63 费用管理：事项申请（通用事项审批单）
--   以"事"为中心：会议/活动/装修/采购/办公等通用事项，
--   走审批流（部门→财务），审批通过作为费用申请/报销的事由依据
--   与"费用申请"（以"钱"为中心+预算占用R1）互补
-- =====================================================

-- 1) 事项申请
CREATE TABLE exp_matter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  matter_no VARCHAR(64) NOT NULL COMMENT '事项编号',
  type VARCHAR(32) NOT NULL COMMENT 'MEETING会议/ACTIVITY活动/RENOVATE装修/PROCURE采购/OFFICE办公/OTHER其他',
  title VARCHAR(200) NOT NULL COMMENT '事项标题',
  description VARCHAR(1000) COMMENT '事项说明',
  dept_id BIGINT NOT NULL,
  user_id BIGINT COMMENT '申请人',
  user_name VARCHAR(64),
  expect_amount DECIMAL(16,2) COMMENT '预计金额(可选)',
  plan_date DATE COMMENT '计划日期(可选)',
  status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_matter_no (matter_no),
  KEY idx_matter_dept (dept_id), KEY idx_matter_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事项申请(通用事项审批单)';

-- 2) 编码规则
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('SX', '事项申请', 'SX-{YYYY}-{seq:4}', 'SX-2026-0001');

-- 3) 审批流：部门经理 → 财务经理
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_EXP_MATTER', '事项申请审批流', 'EXP_MATTER',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"部门经理","assigneeType":"ROLE","assignees":["COST_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"财务经理","assigneeType":"ROLE","assignees":["FIN_MGR"],"countersign":"ANY"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 '事项：部门→财务');

-- 4) 菜单 + 角色授权（1集团管理员 / 2成本经理 / 4财务经理）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(129, 6, '事项申请', 'M', '/expense/matter', 'expense/matter/index', '', 3);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 129), (2, 129), (4, 129);
