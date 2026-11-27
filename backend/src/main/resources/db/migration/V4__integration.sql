-- =====================================================
-- V4 开放集成平台：API注册/密钥/适配器/调用日志
-- =====================================================

CREATE TABLE int_api_registry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  api_code VARCHAR(64) NOT NULL,
  api_name VARCHAR(128) NOT NULL,
  api_path VARCHAR(255) COMMENT '外部系统地址/路径',
  purpose VARCHAR(255),
  rate_limit_qps INT DEFAULT 100,
  owner_user_id BIGINT COMMENT '责任人',
  status VARCHAR(16) DEFAULT 'ENABLED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_api_code (api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放API注册';

CREATE TABLE int_app_key (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_name VARCHAR(128) NOT NULL COMMENT '调用方名称',
  app_key VARCHAR(64) NOT NULL,
  app_secret VARCHAR(128) COMMENT '脱敏展示',
  allowed_apis VARCHAR(1000) COMMENT '授权API编码,逗号分隔',
  status VARCHAR(16) DEFAULT 'ENABLED',
  expire_date DATE,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用方密钥';

CREATE TABLE int_adapter_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adapter_code VARCHAR(64) NOT NULL,
  adapter_name VARCHAR(128) NOT NULL,
  adapter_type VARCHAR(32) COMMENT 'FINANCE财务/ TAX税务/NETSIGN网签/MORTGAGE按揭/IM企微钉钉',
  vendor VARCHAR(64) COMMENT '用友/金蝶/百望...',
  config_json JSON COMMENT '参数化配置(城市/版本差异)',
  sync_direction VARCHAR(16) DEFAULT 'BOTH' COMMENT 'PUSH/_PULL/BOTH',
  status VARCHAR(16) DEFAULT 'ENABLED',
  last_sync_at DATETIME,
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_adapter_code (adapter_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集成适配器';

CREATE TABLE int_call_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adapter_code VARCHAR(64),
  api_code VARCHAR(64),
  direction VARCHAR(8) COMMENT 'IN/OUT',
  request_digest VARCHAR(512),
  response_digest VARCHAR(512),
  success TINYINT,
  cost_ms BIGINT,
  error_msg VARCHAR(512),
  created_at DATETIME,
  KEY idx_call_adapter (adapter_code), KEY idx_call_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用日志';

INSERT INTO int_api_registry (api_code, api_name, api_path, purpose, rate_limit_qps) VALUES
('API_SUPPLIER_SYNC',  '供应商主数据同步', '/open/api/supplier/sync',  '外部采购平台供应商同步', 50),
('API_CONTRACT_PUSH',  '合同信息推送',     '/open/api/contract/push',  '推送财务系统合同台账', 30),
('API_RECEIPT_PULL',   '实付回写拉取',     '/open/api/payment/pull',   '拉取财务实付结果', 30),
('API_INVOICE_CHECK',  '发票查验',         '/open/api/invoice/check',  '税务发票真伪查验', 100),
('API_MORTGAGE_QUERY', '按揭放款查询',     '/open/api/mortgage/query', '银行按揭放款状态', 50);

INSERT INTO int_adapter_config (adapter_code, adapter_name, adapter_type, vendor, config_json, sync_direction, remark) VALUES
('ADP_KINGDEE', '金蝶财务适配器',   'FINANCE',  '金蝶', '{"host":"https://api.kingdee.example","org":"REIP","mapVersion":"v2"}', 'BOTH', '凭证推送/实付回写'),
('ADP_BAIWANG', '税务开票适配器',   'TAX',      '百望', '{"host":"https://api.baiwang.example","encrypt":"SM4"}', 'PUSH', '发票开具与查验'),
('ADP_NETSIGN', '网签备案适配器',   'NETSIGN',  '地方房管', '{"cities":["杭州","南京"],"versionByCity":{"杭州":"v3","南京":"v2"}}', 'BOTH', '按城市扩展接口版本'),
('ADP_BANK',    '银行按揭适配器',   'MORTGAGE', '合作银行', '{"banks":["工商银行","建设银行"]}', 'PULL', '放款状态查询'),
('ADP_WECOM',   '企微组织同步适配器', 'IM',      '企业微信', '{"corpId":"ww_reip_demo","syncContact":true}', 'BOTH', '通讯录双向同步');
