-- =====================================================
-- V40 数字员工团队模块（com.reip.digital）
--   数字员工 = 角色（招采/成本/财务/营销/自定义）
--   技能：内置 + 外部市场通用 + 自定义，全部可编辑
--   任务：定时(CRON) / 条件触发 / 手动执行，全程留痕可回放
--   模型：复用 ai_model_config（DeepSeek/本地/GLM/Kimi/Qwen 均走 OpenAI 兼容协议）
--   表：dig_employee / dig_skill / dig_employee_skill / dig_task / dig_task_run
-- =====================================================

-- ---------- 1. 数字员工（角色） ----------
CREATE TABLE dig_employee (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '数字员工名称,如 招采专员·招招',
  role_code VARCHAR(16) NOT NULL COMMENT 'PROC招采/COST成本/FIN财务/SAL营销/CUSTOM自定义',
  avatar VARCHAR(32) DEFAULT 'custom' COMMENT '形象主题 proc/cost/fin/sal/custom',
  persona TEXT COMMENT '人设设定(系统提示词前缀)',
  model_config_id BIGINT COMMENT '绑定 ai_model_config.id;空=系统默认激活模型',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE在岗/INACTIVE休假',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_role (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字员工(角色)';

-- ---------- 2. 技能（内置/市场/自定义，全部可编辑） ----------
CREATE TABLE dig_skill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  skill_code VARCHAR(64) NOT NULL COMMENT '业务唯一键',
  name VARCHAR(128) NOT NULL,
  category VARCHAR(16) NOT NULL DEFAULT 'BUILTIN' COMMENT 'BUILTIN内置/MARKET市场/CUSTOM自定义',
  data_source VARCHAR(16) DEFAULT 'NONE' COMMENT '取数域 PROC/COST/FIN/SAL/NONE(通用不取数)',
  icon VARCHAR(32) DEFAULT '' COMMENT '前端图标名',
  description VARCHAR(255) COMMENT '技能说明',
  prompt_template TEXT COMMENT '提示词模板,支持 {{data}} {{taskName}} 占位符',
  source VARCHAR(128) COMMENT '来源市场,如 SkillHub',
  install_count INT DEFAULT 0 COMMENT '安装到员工的次数',
  status VARCHAR(16) DEFAULT 'ACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_skill_code (skill_code),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字员工技能';

-- ---------- 3. 员工-技能关联 ----------
CREATE TABLE dig_employee_skill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  sort INT DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_emp (employee_id),
  KEY idx_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工技能关联';

-- ---------- 4. 任务定义（定时/条件/手动） ----------
CREATE TABLE dig_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_code VARCHAR(64) NOT NULL COMMENT '业务唯一键',
  employee_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'SCHEDULED定时/CONDITIONAL条件/MANUAL手动',
  cron_expr VARCHAR(64) COMMENT 'CRON(6段含秒),如 0 0 9 * * 1-5',
  condition_expr VARCHAR(255) COMMENT '条件JSON: {"source":"指标key","op":">=","value":1}',
  cooldown_minutes INT DEFAULT 60 COMMENT '条件触发最小间隔(分钟)',
  status VARCHAR(16) DEFAULT 'ACTIVE',
  last_run_at DATETIME COMMENT '最近一次执行时间',
  next_run_at DATETIME COMMENT '定时任务下次执行时间',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_task_code (task_code),
  KEY idx_emp (employee_id),
  KEY idx_next_run (trigger_type, status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字员工任务定义';

-- ---------- 5. 执行记录（过程留痕，steps 为 JSON 数组） ----------
CREATE TABLE dig_task_run (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_no VARCHAR(32) NOT NULL COMMENT 'RUN-xxx',
  task_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  task_name VARCHAR(128),
  skill_name VARCHAR(128),
  trigger_type VARCHAR(16) COMMENT 'MANUAL/SCHEDULED/CONDITIONAL',
  status VARCHAR(16) DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED',
  started_at DATETIME,
  finished_at DATETIME,
  duration_ms BIGINT,
  model_name VARCHAR(128),
  ai_mode VARCHAR(16) COMMENT 'LLM/RULE',
  output TEXT COMMENT '执行结果正文',
  steps TEXT COMMENT '过程步骤JSON: [{seq,title,status,detail,ts}]',
  error VARCHAR(512),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_emp_time (employee_id, started_at),
  KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字员工任务执行记录';

-- =====================================================
-- 种子数据
-- =====================================================

-- 内置技能：四个角色各 2 个（取真实业务数据 + 提示词模板可编辑）
INSERT INTO dig_skill (skill_code, name, category, data_source, icon, description, prompt_template, source, install_count, status, created_at, updated_at) VALUES
('PROC_LEDGER', '招采台账巡检', 'BUILTIN', 'PROC', 'Files',
 '巡检在办招采项目、待审批定标、定标价偏差与围串标预警，输出巡检报告与行动建议',
 '你是一名房地产企业招采合规专员。请基于以下真实招采数据，生成一份招采巡检报告（200字内），需包含：整体进展、待审批事项、风险提示（定标价偏差/围串标预警）与建议行动。只依据给定数据，不要编造数字。\n招采数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('PROC_AWARD_RISK', '定标风险扫描', 'BUILTIN', 'PROC', 'WarnTriangleFilled',
 '扫描待审批定标单的金额偏差与供应商风险，输出风险清单',
 '你是一名招采风控专家。请基于以下真实数据，输出定标风险扫描结论（150字内）：待审批定标数量、定标价与预算偏差情况、围串标预警类别，并逐条给出风险等级(高/中/低)与处置建议。只依据给定数据，不要编造数字。\n招采数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('COST_DEVIATION', '成本偏差分析', 'BUILTIN', 'COST', 'TrendCharts',
 '对比目标成本与动态成本，输出偏差率、超支项目与管控建议',
 '你是一名房地产成本分析师。请基于以下真实成本数据，生成成本偏差分析（200字内）：目标成本、动态成本、偏差率、超支项目数量、动态利润，并给出成本管控建议。只依据给定数据，不要编造数字。\n成本数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('COST_OVERRUN', '超支预警', 'BUILTIN', 'COST', 'Bell',
 '动态成本超过目标成本的项目预警，输出超支明细与原因分析建议',
 '你是一名成本管控专家。请基于以下真实数据，生成超支预警通报（150字内）：超支项目数量、目标成本与动态成本、偏差率，并给出逐项目核查建议（变更签证/合同价/暂定价）。只依据给定数据，不要编造数字。\n成本数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('FIN_RECEIPT', '回款跟踪分析', 'BUILTIN', 'FIN', 'Money',
 '跟踪签约额与实收回款，计算回款率，输出回款风险提示',
 '你是一名房地产财务分析专家。请基于以下真实数据，生成回款跟踪报告（200字内）：签约额、回款额、回款率、动态利润，评估回款健康度并给出催收与资金安排建议。只依据给定数据，不要编造数字。\n财务数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('FIN_BUDGET', '预算执行分析', 'BUILTIN', 'FIN', 'PieChart',
 '分析签约-回款-成本-利润结构，输出费效与预算执行建议',
 '你是一名集团财务总顾问。请基于以下真实经营数据，生成预算执行分析（200字内）：签约额、回款额、回款率、目标成本、动态成本、动态利润，评估费效结构并给出预算调整建议。只依据给定数据，不要编造数字。\n财务数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('SAL_TURNOVER', '去化分析', 'BUILTIN', 'SAL', 'DataAnalysis',
 '分析各项目去化率与签约回款节奏，输出营销去化建议',
 '你是一名房地产营销顾问。请基于以下真实销售数据，生成去化分析报告（200字内）：签约额、回款额、回款率、去化率偏低项目数量、平均去化率，并给出推盘/价格/渠道建议。只依据给定数据，不要编造数字。\n销售数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('SAL_PRICE', '价格折扣监控', 'BUILTIN', 'SAL', 'PriceTag',
 '监控去化率偏低项目与回款率，提示价格折扣策略调整窗口',
 '你是一名定价策略专家。请基于以下真实数据，生成价格折扣监控报告（150字内）：去化率偏低项目数、平均去化率、回款率，判断是否需要启动特价房审批或渠道促销，并给出折扣区间建议。只依据给定数据，不要编造数字。\n销售数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW());

-- 外部市场通用技能（不取业务数据，通用办公场景）
INSERT INTO dig_skill (skill_code, name, category, data_source, icon, description, prompt_template, source, install_count, status, created_at, updated_at) VALUES
('MKT_WEEKLY_REPORT', '周报生成', 'MARKET', 'NONE', 'Document',
 '输入本周工作内容，生成结构化周报（本周完成/下周计划/需协调事项）',
 '你是一名高效办公助手。请基于以下工作内容，生成一份结构化周报，包含：一、本周完成事项（要点化）；二、下周计划；三、需要协调支持的事项。语言简洁专业。\n工作内容：{{data}}\n任务要求：{{taskName}}',
 'SkillHub', 0, 'ACTIVE', NOW(), NOW()),
('MKT_MINUTES', '会议纪要整理', 'MARKET', 'NONE', 'ChatDotSquare',
 '将会议讨论要点整理为结构化纪要与待办事项清单',
 '你是一名专业的会议秘书。请基于以下会议记录，整理会议纪要：一、会议结论；二、决议事项（含责任人/时间）；三、待办清单（按优先级排序）。不添加记录中不存在的信息。\n会议记录：{{data}}\n任务要求：{{taskName}}',
 'SkillHub', 0, 'ACTIVE', NOW(), NOW()),
('MKT_DOC_POLISH', '公文润色', 'MARKET', 'NONE', 'EditPen',
 '润色公文与报告，规范措辞、理顺结构、消除口语化表达',
 '你是一名资深公文写作顾问。请对以下文稿进行润色：规范公文措辞、理顺段落结构、消除口语化表达，保持原意不变，直接输出润色后的全文。\n文稿：{{data}}\n任务要求：{{taskName}}',
 'SkillHub', 0, 'ACTIVE', NOW(), NOW()),
('MKT_COMPETITOR', '竞品情报摘要', 'MARKET', 'NONE', 'Search',
 '整理竞品动态情报，输出要点摘要与应对建议',
 '你是一名市场研究分析师。请基于以下竞品情报，输出摘要：一、核心要点（3-5条）；二、对我方影响；三、应对建议。只基于给定情报，不补充未提供的事实。\n情报：{{data}}\n任务要求：{{taskName}}',
 'SkillHub', 0, 'ACTIVE', NOW(), NOW());

-- 数字员工（四角色）
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('招采专员·招招', 'PROC', 'proc',
 '你是资深房地产企业招采专家，精通招标采购全流程与合规风控，关注阳光采购、定标价偏差与围串标风险，表达严谨、结论可追溯。',
 'ACTIVE', '负责招采台账巡检与定标风险扫描', NOW(), NOW());
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('成本分析师·算算', 'COST', 'cost',
 '你是资深房地产成本分析师，精通目标成本/动态成本三算对比与变更签证管控，数据敏感，善用偏差率定位超支源头。',
 'ACTIVE', '负责成本偏差分析与超支预警', NOW(), NOW());
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('财务管家·账账', 'FIN', 'fin',
 '你是集团财务分析专家，精通回款管理、预算执行与费效分析，风险意识强，结论注重资金安全与合规。',
 'ACTIVE', '负责回款跟踪与预算执行分析', NOW(), NOW());
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('营销顾问·销销', 'SAL', 'sal',
 '你是房地产营销顾问，精通去化策略、定价折扣与渠道管理，以成交回款为中心，建议可落地。',
 'ACTIVE', '负责去化分析与价格折扣监控', NOW(), NOW());

-- 员工-技能关联（id 1..4 对应上表顺序，技能 id 1..8 为内置技能顺序）
INSERT INTO dig_employee_skill (employee_id, skill_id, sort, created_at, updated_at) VALUES
(1, 1, 1, NOW(), NOW()), (1, 2, 2, NOW(), NOW()),
(2, 3, 1, NOW(), NOW()), (2, 4, 2, NOW(), NOW()),
(3, 5, 1, NOW(), NOW()), (3, 6, 2, NOW(), NOW()),
(4, 7, 1, NOW(), NOW()), (4, 8, 2, NOW(), NOW());
UPDATE dig_skill SET install_count = 1 WHERE skill_code IN
 ('PROC_LEDGER','PROC_AWARD_RISK','COST_DEVIATION','COST_OVERRUN','FIN_RECEIPT','FIN_BUDGET','SAL_TURNOVER','SAL_PRICE');

-- 任务定义：定时 / 条件 / 手动 各形态
INSERT INTO dig_task (task_code, employee_id, skill_id, name, trigger_type, cron_expr, condition_expr, cooldown_minutes, status, next_run_at, remark, created_at, updated_at) VALUES
('TASK-PROC-LEDGER', 1, 1, '招采台账日巡检', 'SCHEDULED', '0 0 9 * * 1-5', NULL, 60, 'ACTIVE', NOW(), '工作日 09:00 自动巡检', NOW(), NOW()),
('TASK-PROC-AWARD', 1, 2, '定标风险扫描', 'CONDITIONAL', NULL, '{"source":"pendingAward","op":">=","value":1}', 120, 'ACTIVE', NULL, '有待审批定标时自动触发', NOW(), NOW()),
('TASK-COST-DEV', 2, 3, '成本偏差日报', 'SCHEDULED', '0 0 10 * * *', NULL, 60, 'ACTIVE', NOW(), '每天 10:00 输出偏差分析', NOW(), NOW()),
('TASK-COST-OVERRUN', 2, 4, '成本超支预警', 'CONDITIONAL', NULL, '{"source":"overrunProjects","op":">","value":0}', 1440, 'ACTIVE', NULL, '出现超支项目时触发', NOW(), NOW()),
('TASK-FIN-RECEIPT', 3, 5, '回款跟踪日报', 'SCHEDULED', '0 0 8 * * *', NULL, 60, 'ACTIVE', NOW(), '每天 08:00 跟踪回款', NOW(), NOW()),
('TASK-FIN-BUDGET', 3, 6, '预算执行分析', 'MANUAL', NULL, NULL, 60, 'ACTIVE', NULL, '月报/经营分析会前手动执行', NOW(), NOW()),
('TASK-SAL-TURNOVER', 4, 7, '去化分析', 'SCHEDULED', '0 0 18 * * *', NULL, 60, 'ACTIVE', NOW(), '每天 18:00 输出去化分析', NOW(), NOW()),
('TASK-SAL-PRICE', 4, 8, '价格折扣监控', 'CONDITIONAL', NULL, '{"source":"lowTurnoverProjects","op":">","value":0}', 1440, 'ACTIVE', NULL, '去化率偏低项目出现时触发', NOW(), NOW());

-- =====================================================
-- 菜单：挂在「数字员工(500)」目录下
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(507, 500, '员工团队', 'M', '/digital/employee', 'digital/employee/index', 'User', 0),
(508, 500, '技能市场', 'M', '/digital/skill',     'digital/skill/index',     'Shop', 1),
(509, 500, '任务中心', 'M', '/digital/run',      'digital/run/index',       'Odometer', 2);

-- 授权：admin(1) 全量；成本(2)/招采(3)/财务(4)/销售经理(6) 可用（含目录 500）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 507), (1, 508), (1, 509),
(2, 500), (2, 507), (2, 508), (2, 509),
(3, 500), (3, 507), (3, 508), (3, 509),
(4, 500), (4, 507), (4, 508), (4, 509),
(6, 500), (6, 507), (6, 508), (6, 509);
