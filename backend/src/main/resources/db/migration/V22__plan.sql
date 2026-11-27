-- =====================================================
-- V22 项目计划（对标明源云计划）：
--   标准库 → 主项/专项 WBS → 进度填报 → 调整审批 → 预警 → 会议任务 → 里程碑 → 形象进度 → 报表
-- 约定：金额/比例 DECIMAL(5,2)；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- 硬校验规则：
--   R1 里程碑锁定：里程碑节点不可直接改计划时间，只能走计划调整；调整里程碑须填延期原因归类
--   R2 前置依赖：后置节点实际开始不得早于其前置节点完成（actual_end）
--   R3 红灯督办：滞后>阈值(RED) 推送集团运营并进入"滞后专项督办"清单
--   R4 形象进度：进度款申请须关联已确认形象进度单，且申请部位%不得高于已确认%
-- 预警阈值：完成=GREEN；滞后<=plan.alert.yellow_days=YELLOW；>该阈值=RED
-- =====================================================

-- 1) 计划标准模板
CREATE TABLE plan_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL COMMENT '模板名称',
  biz_type VARCHAR(32) COMMENT '业态(住宅/商业/车位/综合体)',
  scale VARCHAR(32) COMMENT '规模(大型/中型/小型)',
  tpl_version VARCHAR(16) DEFAULT 'V1' COMMENT '模板版本',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_tpl_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划标准模板';

-- 2) 模板节点库（WBS 标准节点）
CREATE TABLE plan_node_lib (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL COMMENT '所属模板',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  level VARCHAR(16) NOT NULL COMMENT '里程碑/一级/二级/三级',
  base_duration INT COMMENT '基准工期(工天)',
  resp_line VARCHAR(32) COMMENT '责任条线(设计/报建/工程/营销/交付)',
  alert_rule VARCHAR(255) COMMENT '预警规则文本',
  sort_no INT DEFAULT 0 COMMENT '排序',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_node_tpl (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板节点库';

-- 3) 项目计划任务（WBS 实例，主项+专项）
CREATE TABLE plan_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL COMMENT '项目(mdm_project)',
  parent_id BIGINT DEFAULT 0 COMMENT '父节点(WBS)',
  task_code VARCHAR(64) COMMENT '任务编码',
  task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
  level VARCHAR(16) COMMENT '里程碑/一级/二级/三级',
  is_milestone TINYINT DEFAULT 0 COMMENT '1里程碑 0普通',
  specialty VARCHAR(32) COMMENT '专业(设计/报建/工程/营销/交付)',
  plan_start DATE COMMENT '计划开始',
  plan_end DATE COMMENT '计划完成',
  baseline_start DATE COMMENT '基线开始',
  baseline_end DATE COMMENT '基线完成(预警基准)',
  actual_start DATE COMMENT '实际开始',
  actual_end DATE COMMENT '实际完成',
  progress_pct DECIMAL(5,2) DEFAULT 0 COMMENT '完成进度%',
  resp_org_id BIGINT COMMENT '责任部门(mdm_org)',
  resp_user_id BIGINT COMMENT '责任人',
  resp_user_name VARCHAR(64) COMMENT '责任人姓名',
  pre_task_ids VARCHAR(255) COMMENT '前置依赖任务ID(逗号分隔)',
  alert_status VARCHAR(16) DEFAULT 'GREEN' COMMENT 'GREEN/YELLOW/RED',
  status VARCHAR(16) DEFAULT 'PLANNED' COMMENT 'PLANNED/IN_PROGRESS/COMPLETED/DELAYED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_task_project (project_id), KEY idx_task_parent (parent_id),
  KEY idx_task_alert (alert_status), KEY idx_task_milestone (is_milestone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目计划任务(WBS)';

-- 4) 计划调整（时间变更审批）
CREATE TABLE plan_adjust (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adjust_no VARCHAR(64) NOT NULL COMMENT '调整单号',
  task_id BIGINT NOT NULL COMMENT '关联任务',
  reason VARCHAR(500) COMMENT '调整原因',
  reason_category VARCHAR(32) COMMENT '延期原因归类(政策/资金/施工/营销/其他)',
  old_start DATE COMMENT '原计划开始',
  old_end DATE COMMENT '原计划完成',
  new_start DATE COMMENT '新计划开始',
  new_end DATE COMMENT '新计划完成',
  impact_analysis VARCHAR(500) COMMENT '影响分析(下游连锁)',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_adjust_no (adjust_no),
  KEY idx_adjust_task (task_id), KEY idx_adjust_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划调整单';

-- 5) 进度填报（周报）
CREATE TABLE plan_progress_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  report_week VARCHAR(16) COMMENT '报告周(yyyy-ww)',
  task_id BIGINT NOT NULL,
  progress_pct DECIMAL(5,2) COMMENT '填报进度%',
  description VARCHAR(500) COMMENT '进度说明',
  actual_start DATE COMMENT '实际开始',
  actual_end DATE COMMENT '实际完成',
  image_ids VARCHAR(500) COMMENT '影像附件ID(逗号分隔)',
  status VARCHAR(16) DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/CONFIRMED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_prg_project (project_id), KEY idx_prg_task (task_id), KEY idx_prg_week (report_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='进度填报(周报)';

-- 6) 会议
CREATE TABLE plan_meeting (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  meeting_no VARCHAR(64) NOT NULL COMMENT '会议号',
  project_id BIGINT NOT NULL,
  meeting_type VARCHAR(16) DEFAULT 'REGULAR' COMMENT 'REGULAR例会/SPECIAL专题',
  meeting_date DATE COMMENT '会议日期',
  agenda VARCHAR(1000) COMMENT '议题',
  minutes VARCHAR(2000) COMMENT '会议纪要',
  status VARCHAR(16) DEFAULT 'DONE' COMMENT 'DONE/CANCELLED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_meeting_no (meeting_no),
  KEY idx_meeting_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划会议';

-- 7) 会议决议任务
CREATE TABLE meeting_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  meeting_id BIGINT NOT NULL,
  task_desc VARCHAR(500) COMMENT '决议任务描述',
  resp_user_id BIGINT COMMENT '责任人',
  resp_user_name VARCHAR(64) COMMENT '责任人姓名',
  due_date DATE COMMENT '完成期限',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/DONE/OVERDUE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_mtask_meeting (meeting_id), KEY idx_mtask_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议决议任务';

-- 8) 形象进度确认单（供 cost 付款 R4 校验）
CREATE TABLE plan_image_progress (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  building VARCHAR(32) COMMENT '楼栋',
  part_name VARCHAR(64) COMMENT '部位(基础/主体/装修/机电)',
  confirmed_pct DECIMAL(5,2) COMMENT '已确认完成%',
  confirm_date DATE COMMENT '确认日期',
  image_ids VARCHAR(500) COMMENT '影像附件ID(逗号分隔)',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_image_project (project_id), KEY idx_image_part (building, part_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='形象进度确认单';

-- =====================================================
-- 编码规则 + 计数器
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('PLJH', '计划调整', 'PLJH-{YYYY}-{seq:4}', 'PLJH-2026-0001'),
('HTYH', '计划会议', 'HTYH-{YYYY}-{seq:4}', 'HTYH-2026-0001');

-- =====================================================
-- 工作流模板：PLAN_ADJUST(计划调整：项目运营→集团运营；里程碑调整须集团)
-- 审批角色：PLAN_MGR(项目运营,新增) / ADMIN(集团运营)
-- =====================================================
INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_PLAN_ADJUST', '计划调整审批流', 'PLAN_ADJUST',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"项目运营","assigneeType":"ROLE","assignees":["PLAN_MGR"],"countersign":"ANY"},{"id":"n2","type":"APPROVE","name":"集团运营","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n3","type":"END","name":"结束"}],"start":"n1"}',
 '计划调整：项目运营→集团运营会签；里程碑调整必经集团');

-- =====================================================
-- RBAC：菜单 + 角色 + 用户 + 授权（目录 14，子菜单 141-149）
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(14,  0,   '项目计划',   'D', '/plan',         NULL,                      'Calendar',   15),
(141, 14,  '计划标准库', 'M', '/plan/template',  'plan/template/index',   '', 1),
(142, 14,  '主项计划',   'M', '/plan/task',      'plan/task/index',       '', 2),
(143, 14,  '专项计划',   'M', '/plan/specialty', 'plan/specialty/index',  '', 3),
(144, 14,  '进度填报',   'M', '/plan/progress',  'plan/progress/index',   '', 4),
(145, 14,  '计划调整',   'M', '/plan/adjust',    'plan/adjust/index',     '', 5),
(146, 14,  '里程碑管理', 'M', '/plan/milestone', 'plan/milestone/index',  '', 6),
(147, 14,  '会议任务',   'M', '/plan/meeting',   'plan/meeting/index',    '', 7),
(148, 14,  '形象进度',   'M', '/plan/image',     'plan/image/index',      '', 8),
(149, 14,  '计划报表',   'M', '/plan/report',    'plan/report/index',     '', 9);

INSERT INTO sys_role (id, name, code, remark) VALUES
(7, '项目运营', 'PLAN_MGR', '项目计划/审批');

-- admin(1) 全部计划菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 14), (1, 141), (1, 142), (1, 143), (1, 144), (1, 145), (1, 146), (1, 147), (1, 148), (1, 149);
-- 项目运营(7)：全部计划菜单 + 工作台/驾驶舱/审批待办已办我发起
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 7, id FROM sys_menu WHERE id IN (14, 141, 142, 143, 144, 145, 146, 147, 148, 149, 1, 8, 7, 81, 82, 83);
-- 成本经理(2)：计划相关（形象进度/里程碑/调整与成本付款联动）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 14), (2, 141), (2, 142), (2, 145), (2, 146), (2, 148), (2, 149);

-- 项目运营账号（可登录演示）
INSERT INTO sys_user (id, username, password, real_name, mobile, status, org_id, job_level) VALUES
(7, 'planmgr', 'PLAIN:123456', '周计划', '13800000007', 1, 4, 'M3');
INSERT INTO sys_user_role (user_id, role_id) VALUES (7, 7);
-- admin 追加 项目运营 角色：使其可审批计划调整各节点（幂等）
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 7)
ON DUPLICATE KEY UPDATE user_id = user_id;

-- =====================================================
-- 系统参数（业务规则阈值）
-- =====================================================
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('plan.alert.yellow_days', '14',    '预警滞后阈值(天)',   '完成=GREEN；滞后<=N天=YELLOW；>N天=RED'),
('plan.milestone.lock',    'true',  '里程碑锁定开关(R1)', '里程碑节点不可直接改计划时间，只能走计划调整'),
('plan.alert.push_red',    'true',  '红灯推送集团开关(R3)', '滞后>阈值(RED)推送集团运营并进入督办清单');

-- =====================================================
-- 种子：1 个房地产主项模板（节点库：里程碑 + 一级/二级，含基准工期/责任条线/预警规则）
-- =====================================================
INSERT INTO plan_template (name, biz_type, scale, tpl_version, status, created_at, updated_at)
VALUES ('住宅主项标准计划模板', '住宅', '大型', 'V1', 'ACTIVE', NOW(), NOW());

-- 节点库（template_id 取刚建模板 id）
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '拿地',      '里程碑', 90,   '报建',   '滞后>14天红', 1, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '设计',      '一级', 90,   '设计',   '滞后>14天红', 2, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '报建',      '一级', 120,  '报建',   '滞后>14天红', 3, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '开工',      '里程碑', 180,  '工程',   '滞后>14天红', 4, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '工程施工',  '一级', 450,  '工程',   '滞后>14天红', 5, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '正负零',    '里程碑', 120,  '工程',   '滞后>14天红', 6, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '主体施工',  '二级', 120,  '工程',   '滞后>14天红', 7, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '预售条件',  '里程碑', 60,   '营销',   '滞后>14天红', 8, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '营销推广',  '二级', 60,   '营销',   '滞后>14天红', 9, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '开盘',      '里程碑', 30,   '营销',   '滞后>14天红', 10, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '竣备',      '里程碑', 90,   '交付',   '滞后>14天红', 11, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;
INSERT INTO plan_node_lib (template_id, node_name, level, base_duration, resp_line, alert_rule, sort_no, created_at, updated_at)
SELECT t.id, '交付',      '里程碑', 60,   '交付',   '滞后>14天红', 12, NOW(), NOW() FROM plan_template t WHERE t.status='ACTIVE' ORDER BY t.id LIMIT 1;

-- =====================================================
-- 种子：项目1 由模板生成 WBS（含里程碑标记/计划时间/责任人/前置依赖）
-- 状态：1里程碑完成(拿地) / 1进行中(开工) / 1滞后红(正负零)
-- 说明：id 显式指定(新表自增从1)，pre_task_ids 引用任务 id
-- =====================================================
INSERT INTO plan_task (id, project_id, parent_id, task_code, task_name, level, is_milestone, specialty,
  plan_start, plan_end, baseline_start, baseline_end, actual_start, actual_end, progress_pct,
  resp_org_id, resp_user_id, resp_user_name, pre_task_ids, alert_status, status, created_at, updated_at) VALUES
(1,  1, 0, 'P1-01', '拿地',      '里程碑', 1, NULL,   '2025-01-01','2025-03-01','2025-01-01','2025-03-01','2025-01-15','2025-02-25', 100.00, 1, 7, '周计划', NULL,   'GREEN',  'COMPLETED',   NOW(), NOW()),
(2,  1, 0, 'P1-02', '设计',      '一级',   0, '设计', '2025-03-01','2025-05-31','2025-03-01','2025-05-31','2025-03-05','2025-06-05', 100.00, 4, 2, '张成',   '1',     'GREEN',  'COMPLETED',   NOW(), NOW()),
(3,  1, 0, 'P1-03', '报建',      '一级',   0, '报建', '2025-05-01','2025-08-31','2025-05-01','2025-08-31','2025-05-10','2025-08-20', 100.00, 4, 2, '张成',   '2',     'GREEN',  'COMPLETED',   NOW(), NOW()),
(4,  1, 0, 'P1-04', '开工',      '里程碑', 1, NULL,   '2025-09-01','2026-11-30','2025-09-01','2026-11-30','2025-09-15',NULL,        60.00,  1, 7, '周计划', '3',     'GREEN',  'IN_PROGRESS', NOW(), NOW()),
(5,  1, 0, 'P1-05', '工程施工',  '一级',   0, '工程', '2025-09-01','2026-11-30','2025-09-01','2026-11-30','2025-09-15',NULL,        55.00,  4, 2, '张成',   '3',     'GREEN',  'IN_PROGRESS', NOW(), NOW()),
(6,  1, 0, 'P1-06', '正负零',    '里程碑', 1, NULL,   '2026-06-01','2026-07-31','2026-06-01','2026-07-31','2026-06-10',NULL,        30.00,  1, 7, '周计划', '4',     'RED',    'DELAYED',     NOW(), NOW()),
(7,  1, 0, 'P1-07', '主体施工',  '二级',   0, '工程', '2026-08-01','2026-11-30','2026-08-01','2026-11-30',NULL,        NULL,        0.00,   4, 2, '张成',   '5',     'GREEN',  'PLANNED',     NOW(), NOW()),
(8,  1, 0, 'P1-08', '预售条件',  '里程碑', 1, NULL,   '2026-09-01','2026-10-31','2026-09-01','2026-10-31',NULL,        NULL,        0.00,   1, 7, '周计划', '6',     'GREEN',  'PLANNED',     NOW(), NOW()),
(9,  1, 0, 'P1-09', '营销推广',  '二级',   0, '营销', '2026-09-15','2026-11-15','2026-09-15','2026-11-15',NULL,        NULL,        0.00,   5, 6, '钱销售', '8',     'GREEN',  'PLANNED',     NOW(), NOW()),
(10, 1, 0, 'P1-10', '开盘',      '里程碑', 1, NULL,   '2026-11-01','2026-11-30','2026-11-01','2026-11-30',NULL,        NULL,        0.00,   1, 7, '周计划', '8',     'GREEN',  'PLANNED',     NOW(), NOW()),
(11, 1, 0, 'P1-11', '竣备',      '里程碑', 1, NULL,   '2027-06-01','2027-08-31','2027-06-01','2027-08-31',NULL,        NULL,        0.00,   1, 7, '周计划', '10',    'GREEN',  'PLANNED',     NOW(), NOW()),
(12, 1, 0, 'P1-12', '交付',      '里程碑', 1, NULL,   '2027-09-01','2027-10-31','2027-09-01','2027-10-31',NULL,        NULL,        0.00,   1, 7, '周计划', '11',    'GREEN',  'PLANNED',     NOW(), NOW());

-- =====================================================
-- 种子：进度周报 / 例会+决议任务 / 形象进度确认单
-- =====================================================
-- 进度周报（正负零，30%）
INSERT INTO plan_progress_report (project_id, report_week, task_id, progress_pct, description, actual_start, actual_end, image_ids, status, created_at, updated_at)
VALUES (1, '2026-W35', 6, 30.00, '正负零节点滞后，主体钢筋绑扎进行中，需督办', '2026-06-10', NULL, '1,2', 'SUBMITTED', NOW(), NOW());

-- 例会 + 决议任务
INSERT INTO plan_meeting (meeting_no, project_id, meeting_type, meeting_date, agenda, minutes, status, created_at, updated_at)
VALUES ('HTYH-2026-0001', 1, 'REGULAR', '2026-09-01',
        '正负零滞后原因分析；9月形象进度确认；预售条件推进',
        '决议：张成一周内提交正负零赶工方案；周计划牵头预售条件报建', 'DONE', NOW(), NOW());

INSERT INTO meeting_task (meeting_id, task_desc, resp_user_id, resp_user_name, due_date, status, created_at, updated_at)
SELECT m.id, '提交正负零赶工方案', 2, '张成', '2026-09-20', 'PENDING', NOW(), NOW()
FROM plan_meeting m WHERE m.meeting_no = 'HTYH-2026-0001' LIMIT 1;

-- 形象进度确认单（1# 主体完成 55%，已确认；供 cost 进度款 R4 校验）
INSERT INTO plan_image_progress (project_id, building, part_name, confirmed_pct, confirm_date, image_ids, status, created_at, updated_at)
VALUES (1, '1#', '主体结构', 55.00, '2026-08-20', '101,102,103', 'CONFIRMED', NOW(), NOW());

-- =====================================================
-- 编码计数器（按已造单量初始化，避免新单撞号）
-- =====================================================
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('PLJH', DATE_FORMAT(CURDATE(), '%Y'), 0),
('HTYH', DATE_FORMAT(CURDATE(), '%Y'), 1)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));
