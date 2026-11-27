-- =====================================================
-- V25 AI 数字员工模块（com.reip.ai）
--   核心原则：未配大模型 → 规则引擎兜底；配好模型 → 真实 LLM 增强
--   表：模型配置 / 提示词 / 知识库 / 指标语义 / 会话 / 消息 / 任务 / 审计
-- =====================================================

-- ---------- 1. 大模型配置（密钥加密存储，展示脱敏） ----------
CREATE TABLE ai_model_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider VARCHAR(32) NOT NULL COMMENT 'OPENAI_COMPATIBLE/QWEN/DEEPSEEK',
  model_name VARCHAR(128) COMMENT '模型名,如 gpt-4o / qwen-max / deepseek-chat',
  base_url VARCHAR(255) COMMENT 'OpenAI 兼容 base_url(含/v1)',
  api_key VARCHAR(512) COMMENT 'API Key(AES加密存储)',
  timeout_ms INT DEFAULT 60000 COMMENT '调用超时(毫秒)',
  temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度',
  status VARCHAR(16) DEFAULT 'INACTIVE' COMMENT 'ACTIVE启用/INACTIVE停用',
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI大模型配置';

-- ---------- 2. 提示词模板 ----------
CREATE TABLE ai_prompt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  prompt_code VARCHAR(64) NOT NULL COMMENT '业务唯一键,如 APPROVAL_PREVIEW/QUERY/REPORT_SALES',
  name VARCHAR(128) NOT NULL,
  scene VARCHAR(32) NOT NULL COMMENT 'APPROVAL_PREVIEW/QUERY/REPORT/QA',
  template_text TEXT COMMENT '提示词正文, {{key}} 占位符',
  prompt_version INT DEFAULT 1 COMMENT '提示词版本(业务)',
  status VARCHAR(16) DEFAULT 'ACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_prompt_code (prompt_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI提示词模板';

-- ---------- 3. 知识库文档（RAG，embedding 留占位） ----------
CREATE TABLE ai_kb_document (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_code VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  category VARCHAR(32) COMMENT '制度/指标/案例',
  content TEXT,
  embedding_ready TINYINT DEFAULT 0 COMMENT '1=已向量化',
  status VARCHAR(16) DEFAULT 'ACTIVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_doc_code (doc_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库文档';

-- ---------- 4. 指标语义层（NL→指标，R1 语义层） ----------
CREATE TABLE ai_metric_semantic (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  metric_code VARCHAR(64) NOT NULL COMMENT '关联 metric_def.metric_code',
  metric_name VARCHAR(128),
  keywords VARCHAR(512) COMMENT '分词/同义词,逗号分隔',
  description VARCHAR(512),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_metric_code (metric_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI指标语义映射';

-- ---------- 5. 聊天会话 ----------
CREATE TABLE ai_chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL COMMENT '会话业务ID(UUID)',
  user_id BIGINT COMMENT '所属用户',
  title VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话';

-- ---------- 6. 聊天消息 ----------
CREATE TABLE ai_chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT',
  content TEXT,
  ai_mode VARCHAR(16) COMMENT 'RULE规则/LLM大模型',
  refs TEXT COMMENT '引用单据/指标JSON',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息';

-- ---------- 7. 异步任务中心 ----------
CREATE TABLE ai_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_code VARCHAR(64) COMMENT '任务业务码',
  type VARCHAR(16) NOT NULL COMMENT 'OCR/REPORT/PREVIEW',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  title VARCHAR(255),
  input TEXT COMMENT '输入JSON',
  output TEXT COMMENT '输出JSON',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI异步任务';

-- ---------- 8. AI 调用审计（R3 留痕） ----------
CREATE TABLE ai_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene VARCHAR(32) COMMENT 'APPROVAL_PREVIEW/QUERY/REPORT/QA/MODEL_TEST',
  user_id BIGINT,
  model_config_id BIGINT,
  input_digest TEXT COMMENT '输入脱敏摘要',
  output_digest TEXT COMMENT '输出脱敏摘要',
  token_in INT,
  token_out INT,
  cost DECIMAL(10,4),
  duration_ms BIGINT,
  ai_mode VARCHAR(16) COMMENT 'RULE/LLM',
  status VARCHAR(16) COMMENT 'SUCCESS/FAIL/DEGRADED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_scene (scene),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用审计日志';

-- =====================================================
-- 种子数据
-- =====================================================

-- 模型配置占位（INACTIVE，演示用，无有效 key）
INSERT INTO ai_model_config (provider, model_name, base_url, api_key, timeout_ms, temperature, status, remark, created_at, updated_at) VALUES
('OPENAI_COMPATIBLE', 'gpt-4o', 'https://api.openai.com/v1', NULL, 60000, 0.70, 'INACTIVE', '占位：填入 base_url/api_key 并置 ACTIVE 即启用真实大模型', NOW(), NOW());

-- 提示词模板（4 场景各 1 条 + 报告分类型）
INSERT INTO ai_prompt (prompt_code, name, scene, template_text, prompt_version, status, created_at, updated_at) VALUES
('APPROVAL_PREVIEW', '审批预审解读', 'APPROVAL_PREVIEW',
 '你是一名房地产一体化平台的审批助手。请基于以下结构化单据与规则检测结果，生成一段 150 字内的自然语言预审意见，仅给出风险提示与建议，不做最终审批决定。\n单据类型：{{bizType}}\n摘要：{{summary}}\n规则风险：{{risks}}\n相似单据：{{similar}}',
 1, 'ACTIVE', NOW(), NOW()),
('QUERY', '指标问答解读', 'QUERY',
 '你是数据分析师。用户问题：{{question}}。已命中指标：{{metricName}}，取值：{{value}}，口径：{{caliber}}。请用一句话给出结论，并提示数据口径与来源，不要编造未在输入中出现的数据。',
 1, 'ACTIVE', NOW(), NOW()),
('REPORT_SALES', '销售日报解读', 'REPORT',
 '你是销售运营分析师。基于以下真实数据生成"解读与建议"章节（200字内）：\n签约额：{{signedAmount}}，回款额：{{receiptAmount}}，回款率：{{receiptRate}}，去化率：{{turnoverRate}}。\n只依据给定数据，不编造数字。',
 1, 'ACTIVE', NOW(), NOW()),
('REPORT_COST', '成本月报解读', 'REPORT',
 '你是成本分析师。基于以下真实数据生成"解读与建议"章节（200字内）：\n目标成本：{{targetCost}}，动态成本：{{dynamicCost}}，偏差率：{{deviationRate}}，实际成本：{{actualCost}}。\n只依据给定数据，不编造数字。',
 1, 'ACTIVE', NOW(), NOW()),
('REPORT_PROC', '采购分析解读', 'REPORT',
 '你是招采分析师。基于以下真实数据生成"解读与建议"章节（200字内）：\n立项数：{{totalProjects}}，公开招标数：{{publicBidCount}}，阳光度：{{sunshineRatio}}%，预算：{{budgetAmount}}，定标：{{awardAmount}}。\n只依据给定数据，不编造数字。',
 1, 'ACTIVE', NOW(), NOW());

-- 知识库文档（制度/指标/案例，供检索）
INSERT INTO ai_kb_document (doc_code, title, category, content, embedding_ready, status, created_at, updated_at) VALUES
('KB_POLICY_001', '折扣审批刚性制度', '制度',
 '置业顾问权限上限外的成交折扣，必须先完成特价房审批方可提交签约审批；系统按 sys_param 阈值自动拦截，AI 仅提示不代批。', 0, 'ACTIVE', NOW(), NOW()),
('KB_METRIC_001', '回款率口径', '指标',
 '回款率 = 实收回款额 / 签约额 × 100%。来源 sale_receipt 实收 与 sale_contract 成交(生效/网签)。', 0, 'ACTIVE', NOW(), NOW()),
('KB_METRIC_002', '动态成本口径', '指标',
 '动态成本取每个项目最新月度快照 cost_dynamic_snapshot.dynamic_total；目标成本同表 target_total；偏差率=(动态-目标)/目标。', 0, 'ACTIVE', NOW(), NOW()),
('KB_CASE_001', '供应商黑名单处置案例', '案例',
 '某项目定标供应商处于黑名单(mdm_supplier.lib_type=BLACKLIST)，系统规则引擎在审批预审中标红提示并附依据，审批人据此驳回。', 0, 'ACTIVE', NOW(), NOW());

-- 指标语义映射（NL→指标，覆盖核心指标）
INSERT INTO ai_metric_semantic (metric_code, metric_name, keywords, description, created_at, updated_at) VALUES
('SIGN_AMOUNT', '签约额', '签约额,签约,成交,合同额,销售金额,销售额', '生效/网签合同成交总价合计', NOW(), NOW()),
('RECEIPT_AMOUNT', '回款额', '回款额,回款,实收,收款,到账,回款金额', '销售实收回款合计', NOW(), NOW()),
('RECEIPT_RATE', '回款率', '回款率,回款比例,实收率,回款比', '回款率=实收/签约×100%', NOW(), NOW()),
('TURNOVER_RATE', '去化率', '去化率,去化,动销,售去化,售出率,售罄率', '已售货值/总货值(项目均值)', NOW(), NOW()),
('DYNAMIC_COST', '动态成本', '动态成本,成本,建安成本,目标成本,成本额', '各项目最新月度动态成本合计', NOW(), NOW()),
('DYNAMIC_PROFIT', '动态利润', '动态利润,利润,净利润,项目利润', '各项目最新月度动态利润合计', NOW(), NOW()),
('COST_EFFICIENCY', '费效比', '费效比,费用率,三费,销售费效,营销费,管理费率', '三费构成及费效(费用/签约)', NOW(), NOW());

-- =====================================================
-- 菜单：数字员工 目录 + 6 子菜单，授予 admin(集团管理员 role 1)
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(500, 0, '数字员工', 'D', '/ai', NULL, 'Cpu', 60),
(501, 500, '审批助手', 'M', '/ai/approval', 'ai/approval/index', '', 1),
(502, 500, 'AI问答',   'M', '/ai/chat',     'ai/chat/index',     '', 2),
(503, 500, '报告生成', 'M', '/ai/report',   'ai/report/index',   '', 3),
(504, 500, '知识库',   'M', '/ai/kb',       'ai/kb/index',       '', 4),
(505, 500, '模型配置', 'M', '/ai/model',    'ai/model/index',    '', 5),
(506, 500, '审计日志', 'M', '/ai/audit',    'ai/audit/index',    '', 6);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 500), (1, 501), (1, 502), (1, 503), (1, 504), (1, 505), (1, 506);
