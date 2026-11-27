-- =====================================================
-- V71 设计审图（读图/审图，com.reip.dwg）
--   流程：上传 CAD/设计图纸 → AI/系统预审(生成审查要点) → 人工审查逐条提出/确认
--         → 逐条解决(或标记无需处理) → 审查通过 / 驳回
--   AI 原则（同 AiApprovalService）：未配大模型 → 规则引擎产出审查要点(确定性、可用)；
--     配好模型 → LLM 生成自然语言预审意见(增强)；AI 仅提示，不代审，最终由人工判定。
--   表：图纸审查单 dwg_drawing / 审查问题 dwg_issue
-- =====================================================

-- 1) 图纸审查单（一次上传 + 一次审查）
CREATE TABLE dwg_drawing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  drawing_code VARCHAR(64) NOT NULL COMMENT '审查单号 STG-{YYYY}-{seq:4}',
  project_id BIGINT NOT NULL COMMENT '项目(mdm_project)',
  discipline VARCHAR(32) NOT NULL COMMENT '专业 ARCH建筑/STRUCT结构/ELEC电气/PLUMB给排水/HVAC暖通/INTERIOR精装/LANDSCAPE景观',
  title VARCHAR(255) NOT NULL COMMENT '图纸标题',
  drawing_version VARCHAR(32) COMMENT '图版/版本，如 A、B、V1.0',
  stage VARCHAR(32) COMMENT '阶段 SCHEME方案/CONSTRUCTION施工图/AS_BUILT竣工图',
  design_unit VARCHAR(128) COMMENT '设计单位(可空)',
  file_name VARCHAR(255) NOT NULL COMMENT '原文件名',
  file_path VARCHAR(512) NOT NULL COMMENT '相对存储路径(uploads/dwg/...)',
  file_size BIGINT COMMENT '文件大小(字节)',
  page_count INT COMMENT '页数(可空)',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING待审/REVIEWING审查中/PASSED通过/REJECTED驳回',
  ai_mode VARCHAR(16) COMMENT '最近一次预审模式 RULE规则引擎/LLM大模型',
  ai_note TEXT COMMENT 'AI预审意见(自然语言, 可空)',
  reviewer_name VARCHAR(64) COMMENT '主审人(冗余展示)',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_dwg_code (drawing_code),
  KEY idx_dwg_project (project_id),
  KEY idx_dwg_status (status),
  KEY idx_dwg_discipline (discipline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设计图纸审查单';

-- 2) 审查问题（AI 生成 + 人工提出，逐条解决）
CREATE TABLE dwg_issue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  drawing_id BIGINT NOT NULL COMMENT '所属审查单(dwg_drawing)',
  seq INT COMMENT '序号(展示用)',
  source VARCHAR(16) NOT NULL COMMENT '来源 AI系统预审/MANUAL人工提出',
  level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重度 HIGH严重/MEDIUM一般/LOW建议',
  category VARCHAR(32) COMMENT '分类 图面表达/规范符合性/尺寸标注/专业冲突/材料设备/安全消防/节能环保/其他',
  location VARCHAR(128) COMMENT '图号/部位/轴号(可空)',
  description VARCHAR(1000) NOT NULL COMMENT '问题描述',
  suggestion VARCHAR(500) COMMENT '修改建议(可空)',
  status VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN待解决/RESOLVED已解决/REJECTED无需处理',
  assignee_name VARCHAR(64) COMMENT '责任人(可空)',
  resolve_note VARCHAR(500) COMMENT '解决说明',
  resolver_id BIGINT COMMENT '解决人(sys_user)',
  resolve_date DATE COMMENT '解决日期',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_issue_drawing (drawing_id),
  KEY idx_issue_status (status),
  KEY idx_issue_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸审查问题(逐条解决)';

-- =====================================================
-- 编码规则 + 计数器
-- =====================================================
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('STG', '审图单号', 'STG-{YYYY}-{seq:4}', 'STG-2026-0001');
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no) VALUES
('STG', DATE_FORMAT(CURDATE(), '%Y'), 0);

-- =====================================================
-- AI 提示词：图纸预审（RULE 兜底 + LLM 增强）
-- =====================================================
INSERT INTO ai_prompt (prompt_code, name, scene, template_text, prompt_version, status, created_at, updated_at) VALUES
('DRAWING_REVIEW', '设计图纸预审', 'REVIEW',
 '你是一名建筑工程设计审图专家。请基于以下图纸信息与规则引擎给出的审查要点，给出一段 200 字内的预审意见：聚焦错漏碰缺、规范符合性与专业配合风险，并提示需人工重点核实的要点。\n仅给出审查方向与建议，不要编造图纸中未提供的具体数值、结论或规范条文编号。\n项目：{{projectName}}\n专业：{{discipline}}\n阶段：{{stage}}\n图纸：{{title}}（{{version}}）\n文件：{{fileName}}（{{fileSize}}）\n审查要点(规则引擎)：{{points}}',
 1, 'ACTIVE', NOW(), NOW());

-- =====================================================
-- 菜单：工程审图 目录(600) + 图纸审查 子菜单(601)
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(600, 0,   '工程审图', 'D', '/dwg',       NULL,               'Document', 70),
(601, 600, '图纸审查', 'M', '/dwg/review','dwg/review/index', '', 1);

-- 角色授权：集团管理员(1) / 成本经理(2) / 项目运营(7)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 600), (1, 601),
(2, 600), (2, 601),
(7, 600), (7, 601);
