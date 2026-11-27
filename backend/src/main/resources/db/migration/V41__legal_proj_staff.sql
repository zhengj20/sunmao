-- =====================================================
-- V41 法务 + 项目工程师 数字员工
-- =====================================================

-- ---------- 法务技能 ----------
INSERT INTO dig_skill (skill_code, name, category, data_source, icon, description, prompt_template, source, install_count, status, created_at, updated_at) VALUES
('LEGAL_CONTRACT', '合同风险审查', 'BUILTIN', 'NONE', 'DocumentChecked',
 '审查合同关键条款，识别法律风险点，输出风险清单与处置建议',
 '你是一名资深房地产企业法务顾问，精通合同法、招投标法与建设工程法规。请基于以下合同要点，输出风险审查报告（200字内）：一、关键条款合规性；二、风险点清单（高/中/低）；三、修改建议与替代方案。只依据给定要点，不编造条款。\n合同要点：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('LEGAL_COMPLIANCE', '合规巡检', 'BUILTIN', 'NONE', 'Stamp',
 '对招采/成本/营销等业务环节进行合规性检查，输出合规差距与整改建议',
 '你是一名房地产企业合规官，精通行业监管要求与内部制度。请基于以下业务描述，输出合规巡检报告（200字内）：一、适用法规与制度依据；二、合规差距（按严重程度排序）；三、整改建议与责任建议。只基于给定信息，不虚构制度条款。\n业务描述：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW());

-- ---------- 项目工程师技能 ----------
INSERT INTO dig_skill (skill_code, name, category, data_source, icon, description, prompt_template, source, install_count, status, created_at, updated_at) VALUES
('PROJ_SCHEDULE', '工程进度跟踪', 'BUILTIN', 'COST', 'Calendar',
 '基于项目成本数据跟踪进度与成本偏差，输出进度预警与纠偏建议',
 '你是一名资深房地产项目工程师，精通进度管理、成本控制与变更签证。请基于以下真实项目数据，输出工程进度跟踪报告（200字内）：一、整体进度与成本状态；二、偏差项目（超支/滞后）清单；三、纠偏措施与资源调配建议。只依据给定数据，不编造进度节点。\n项目数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW()),
('PROJ_QUALITY', '质量安全巡检', 'BUILTIN', 'COST', 'FirstAidKit',
 '结合成本偏差识别质量安全隐患，输出巡检清单与整改要求',
 '你是一名建筑工程质量安全总监，精通施工质量标准与安全规范。请基于以下项目数据，输出质量安全巡检报告（150字内）：一、重点关注项目（超支/偏差）；二、潜在质量安全隐患；三、巡检要点与整改要求（含责任层级）。只依据给定数据，不虚构隐患。\n项目数据：{{data}}\n任务要求：{{taskName}}',
 NULL, 0, 'ACTIVE', NOW(), NOW());

-- ---------- 法务员工 ----------
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('法务顾问·律律', 'LEGAL', 'legal',
 '你是资深房地产企业法务顾问，精通合同法、招投标法、建设工程法规与合规管理，风险意识强，建议可落地、条款可追溯。',
 'ACTIVE', '负责合同风险审查与合规巡检', NOW(), NOW());

-- ---------- 项目工程师 ----------
INSERT INTO dig_employee (name, role_code, avatar, persona, status, remark, created_at, updated_at) VALUES
('项目工程师·工工', 'PROJ', 'proj',
 '你是资深房地产项目工程师，精通进度管理、成本控制与质量安全，数据驱动，建议具体可执行，注重工期与成本平衡。',
 'ACTIVE', '负责工程进度跟踪与质量安全巡检', NOW(), NOW());

-- ---------- 员工-技能关联（用子查询避免依赖自增 ID） ----------
INSERT INTO dig_employee_skill (employee_id, skill_id, sort, created_at, updated_at) VALUES
((SELECT id FROM dig_employee WHERE name = '法务顾问·律律'), (SELECT id FROM dig_skill WHERE skill_code = 'LEGAL_CONTRACT'), 1, NOW(), NOW()),
((SELECT id FROM dig_employee WHERE name = '法务顾问·律律'), (SELECT id FROM dig_skill WHERE skill_code = 'LEGAL_COMPLIANCE'), 2, NOW(), NOW()),
((SELECT id FROM dig_employee WHERE name = '项目工程师·工工'), (SELECT id FROM dig_skill WHERE skill_code = 'PROJ_SCHEDULE'), 1, NOW(), NOW()),
((SELECT id FROM dig_employee WHERE name = '项目工程师·工工'), (SELECT id FROM dig_skill WHERE skill_code = 'PROJ_QUALITY'), 2, NOW(), NOW());

UPDATE dig_skill SET install_count = 1 WHERE skill_code IN
('LEGAL_CONTRACT','LEGAL_COMPLIANCE','PROJ_SCHEDULE','PROJ_QUALITY');

-- ---------- 任务定义 ----------
INSERT INTO dig_task (task_code, employee_id, skill_id, name, trigger_type, cron_expr, condition_expr, cooldown_minutes, status, next_run_at, remark, created_at, updated_at) VALUES
('TASK-LEGAL-CONTRACT',
 (SELECT id FROM dig_employee WHERE name = '法务顾问·律律'),
 (SELECT id FROM dig_skill WHERE skill_code = 'LEGAL_CONTRACT'),
 '合同风险日报', 'SCHEDULED', '0 30 10 * * *', NULL, 60, 'ACTIVE', NOW(),
 '每天 10:30 自动审查合同风险', NOW(), NOW()),
('TASK-LEGAL-COMPLIANCE',
 (SELECT id FROM dig_employee WHERE name = '法务顾问·律律'),
 (SELECT id FROM dig_skill WHERE skill_code = 'LEGAL_COMPLIANCE'),
 '合规巡检', 'MANUAL', NULL, NULL, 60, 'ACTIVE', NULL,
 '合规审计/监管检查前手动执行', NOW(), NOW()),
('TASK-PROJ-SCHEDULE',
 (SELECT id FROM dig_employee WHERE name = '项目工程师·工工'),
 (SELECT id FROM dig_skill WHERE skill_code = 'PROJ_SCHEDULE'),
 '工程进度周报', 'SCHEDULED', '0 0 9 * * 1', NULL, 60, 'ACTIVE', NOW(),
 '每周一 09:00 输出进度周报', NOW(), NOW()),
('TASK-PROJ-QUALITY',
 (SELECT id FROM dig_employee WHERE name = '项目工程师·工工'),
 (SELECT id FROM dig_skill WHERE skill_code = 'PROJ_QUALITY'),
 '质量安全巡检', 'CONDITIONAL', NULL,
 '{"source":"overrunProjects","op":">","value":0}', 1440, 'ACTIVE', NULL,
 '出现超支项目时自动触发质量安全巡检', NOW(), NOW());
