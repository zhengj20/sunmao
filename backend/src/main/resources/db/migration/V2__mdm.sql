-- =====================================================
-- V2 主数据 MDM：组织/员工/项目/房源/科目/字典/供应商/客户/编码规则
-- =====================================================

CREATE TABLE mdm_org (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT 0,
  name VARCHAR(128) NOT NULL,
  code VARCHAR(64) COMMENT '组织编码',
  org_type VARCHAR(32) COMMENT 'GROUP集团/REGION区域/CITY城市/PROJECT项目公司/DEPT部门',
  leader_user_id BIGINT COMMENT '负责人',
  cost_center_code VARCHAR(64) COMMENT '成本中心映射',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FROZEN/CANCELLED',
  sort INT DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_org_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织机构';

CREATE TABLE mdm_employee (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT COMMENT '关联登录账号',
  emp_no VARCHAR(32) COMMENT '工号',
  real_name VARCHAR(64) NOT NULL,
  org_id BIGINT,
  position VARCHAR(64) COMMENT '岗位',
  job_level VARCHAR(16) COMMENT '职务级别',
  mobile VARCHAR(32),
  email VARCHAR(128),
  id_card VARCHAR(32) COMMENT '证件号(脱敏展示)',
  entry_date DATE,
  leave_date DATE,
  status VARCHAR(16) DEFAULT 'IN_SERVICE' COMMENT 'IN_SERVICE/TRANSFER/LEAVE',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_emp_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案';

CREATE TABLE mdm_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_code VARCHAR(32) NOT NULL COMMENT '项目编号',
  name VARCHAR(128) NOT NULL COMMENT '项目名称',
  org_id BIGINT COMMENT '所属组织(区域)',
  city VARCHAR(64),
  address VARCHAR(255),
  cooperation_mode VARCHAR(32) COMMENT '独资/合作/代建',
  partner VARCHAR(255) COMMENT '合作方',
  land_acquired_date DATE COMMENT '拿地日期',
  land_area DECIMAL(14,2) COMMENT '占地面积(㎡)',
  far_area DECIMAL(14,2) COMMENT '计容面积(㎡)',
  saleable_area DECIMAL(14,2) COMMENT '可售面积(㎡)',
  plot_ratio DECIMAL(8,3) COMMENT '容积率',
  land_cost DECIMAL(16,2) COMMENT '土地款(元)',
  status VARCHAR(16) DEFAULT 'PREPARING' COMMENT 'PREPARING筹备/CONSTRUCTION在建/DELIVERED交付/LIQUIDATION清算',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目主数据';

CREATE TABLE mdm_phase (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  phase_code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  far_area DECIMAL(14,2),
  saleable_area DECIMAL(14,2),
  status VARCHAR(16) DEFAULT 'PREPARING',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_phase_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目分期';

CREATE TABLE mdm_product_type (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL COMMENT '业态名称',
  category VARCHAR(32) COMMENT '住宅/商业/车位/配套',
  saleable TINYINT DEFAULT 1 COMMENT '1可售 0不可售',
  far_area DECIMAL(14,2),
  saleable_area DECIMAL(14,2),
  sort INT DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_pt_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业态定义';

CREATE TABLE mdm_building (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  phase_id BIGINT,
  building_code VARCHAR(32) NOT NULL COMMENT '栋号',
  name VARCHAR(64),
  floors INT COMMENT '地上层数',
  underground_floors INT DEFAULT 0,
  units INT DEFAULT 1 COMMENT '单元数',
  total_area DECIMAL(14,2),
  status VARCHAR(16) DEFAULT 'PLANNED' COMMENT 'PLANNED/CONSTRUCTING/COMPLETED',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_building_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋';

CREATE TABLE mdm_room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  building_id BIGINT NOT NULL,
  room_code VARCHAR(64) NOT NULL COMMENT '房间唯一编号',
  floor_no INT,
  unit_no INT,
  room_no VARCHAR(16),
  layout VARCHAR(32) COMMENT '户型',
  orientation VARCHAR(32) COMMENT '朝向',
  area DECIMAL(12,2) COMMENT '建筑面积',
  inner_area DECIMAL(12,2) COMMENT '套内面积',
  product_type_id BIGINT,
  saleable TINYINT DEFAULT 1 COMMENT '可售标记',
  sale_status VARCHAR(16) DEFAULT 'INIT' COMMENT 'INIT初始/LOCKED锁定/SOLD售出(销售域回写)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_room_code (room_code),
  KEY idx_room_building (building_id), KEY idx_room_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间(房间生成器产出)';

CREATE TABLE mdm_cost_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT COMMENT 'NULL=集团模板',
  parent_id BIGINT DEFAULT 0,
  code VARCHAR(64) NOT NULL COMMENT '科目编码',
  name VARCHAR(128) NOT NULL,
  level INT COMMENT '级次1-6',
  leaf TINYINT DEFAULT 0,
  control_mode VARCHAR(32) COMMENT '控制方式：刚性/预警/不限',
  allocate_rule VARCHAR(64) COMMENT '分摊规则',
  finance_account VARCHAR(64) COMMENT '财务核算科目映射',
  sort INT DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_account_project (project_id), KEY idx_account_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本科目树(集团模板+项目克隆)';

CREATE TABLE mdm_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_type VARCHAR(32) NOT NULL COMMENT 'CONTRACT合同分类/PURCHASE采购品类/EXPENSE费用科目/CHANGE变更分类',
  parent_id BIGINT DEFAULT 0,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  sort INT DEFAULT 0,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_cat_type (category_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类树(合同/采购品类/费用科目/变更)';

CREATE TABLE mdm_dict (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_key VARCHAR(64) NOT NULL,
  label VARCHAR(128) NOT NULL,
  value VARCHAR(64) NOT NULL,
  sort INT DEFAULT 0,
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_dict_key (dict_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一码值字典';

CREATE TABLE mdm_supplier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  short_name VARCHAR(64),
  credit_code VARCHAR(32) NOT NULL COMMENT '统一社会信用代码(唯一)',
  category_id BIGINT COMMENT '品类分类',
  classify VARCHAR(32) COMMENT '工程/材料/服务/设备',
  grade VARCHAR(16) COMMENT 'A/B/C/D',
  lib_type VARCHAR(16) DEFAULT 'PRESELECT' COMMENT 'OFFICIAL正式库/PRESELECT预选库/BLACKLIST黑名单',
  legal_person VARCHAR(64),
  registered_capital DECIMAL(14,2),
  contact_person VARCHAR(64),
  contact_phone VARCHAR(32),
  address VARCHAR(255),
  performance_score DECIMAL(6,2) COMMENT '履约评分',
  blacklist_reason VARCHAR(255),
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_credit_code (credit_code),
  KEY idx_supplier_lib (lib_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商主数据';

CREATE TABLE mdm_supplier_qualification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  qual_type VARCHAR(64) COMMENT '营业执照/资质证书/安全生产许可证',
  qual_no VARCHAR(64),
  issue_date DATE,
  expire_date DATE,
  file_id BIGINT COMMENT '影像附件',
  ocr_status TINYINT DEFAULT 0 COMMENT '1=OCR已识别',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_qual_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商资质证照';

CREATE TABLE mdm_customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  cert_type VARCHAR(16) DEFAULT 'ID_CARD' COMMENT 'ID_CARD身份证/PASSPORT/BIZ_LICENSE',
  cert_no VARCHAR(32) NOT NULL,
  customer_type VARCHAR(16) DEFAULT 'PERSON' COMMENT 'PERSON个人/ENTERPRISE企业',
  mobile VARCHAR(32),
  address VARCHAR(255),
  advisor_user_id BIGINT COMMENT '归属置业顾问(二期销售)',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_cert (cert_type, cert_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户主数据';

CREATE TABLE mdm_customer_relative (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  name VARCHAR(64),
  cert_no VARCHAR(32),
  relation VARCHAR(32) COMMENT '共同购买人/配偶/担保人',
  phone VARCHAR(32),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_rel_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户关联人';

CREATE TABLE mdm_code_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
  name VARCHAR(128) NOT NULL,
  pattern VARCHAR(128) NOT NULL COMMENT '如 HT-{projectCode}-{YYYY}-{seq:4}',
  example VARCHAR(64),
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编码规则';

CREATE TABLE mdm_code_counter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code VARCHAR(32) NOT NULL,
  dim_key VARCHAR(64) NOT NULL,
  current_no BIGINT DEFAULT 0,
  UNIQUE KEY uk_rule_dim (rule_code, dim_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编码计数器(行锁并发安全)';

CREATE TABLE mdm_distribute_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  data_type VARCHAR(32) COMMENT 'org/project/supplier/customer',
  data_id BIGINT,
  target_domain VARCHAR(32) COMMENT 'cost/procurement/sales...',
  status VARCHAR(16) DEFAULT 'SUCCESS',
  error_msg VARCHAR(512),
  created_at DATETIME,
  KEY idx_dist_data (data_type, data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主数据分发日志';

-- ========== 种子：编码规则 ==========
INSERT INTO mdm_code_rule (rule_code, name, pattern, example) VALUES
('PRJ',   '项目编号',   'PJ{YYYY}{seq:3}',            'PJ2026001'),
('CGXQ',  '采购需求',   'XQ-{YYYY}{MM}-{seq:4}',      'XQ-202609-0001'),
('CGXM',  '招采立项',   'ZC-{YYYY}{MM}-{seq:4}',      'ZC-202609-0001'),
('FB',    '发标',       'FB-{YYYY}{MM}-{seq:4}',      'FB-202609-0001'),
('DB',    '定标',       'DB-{YYYY}{MM}-{seq:4}',      'DB-202609-0001'),
('HT',    '合同',       'HT-{projectCode}-{YYYY}-{seq:4}', 'HT-PJ2026001-2026-0001'),
('BG',    '变更单',     'BG-{projectCode}-{YYYY}-{seq:4}', 'BG-PJ2026001-2026-0001'),
('FK',    '付款单',     'FK-{projectCode}-{YYYY}-{seq:4}', 'FK-PJ2026001-2026-0001'),
('MB',    '目标成本',   'MB-{projectCode}-{YYYY}-{seq:3}', 'MB-PJ2026001-2026-001'),
('HYGH',  '合约规划',   'GH-{projectCode}-{YYYY}-{seq:3}', 'GH-PJ2026001-2026-001'),
('GYS',   '供应商',     'GYS-{YYYY}{seq:4}',          'GYS20260001'),
('KH',    '客户',       'KH-{YYYY}{seq:5}',           'KH202600001');

-- ========== 种子：集团成本科目模板(6大类二级示例) ==========
INSERT INTO mdm_cost_account (id, project_id, parent_id, code, name, level, leaf, control_mode, sort) VALUES
(1,  NULL, 0, '01',   '土地获得成本',   1, 0, '刚性', 1),
(2,  NULL, 1, '0101', '土地出让金',     2, 1, '刚性', 1),
(3,  NULL, 1, '0102', '契税及费用',     2, 1, '刚性', 2),
(4,  NULL, 0, '02',   '开发前期费用',   1, 0, '预警', 2),
(5,  NULL, 4, '0201', '勘察设计费',     2, 1, '预警', 1),
(6,  NULL, 4, '0202', '报批报建费',     2, 1, '预警', 2),
(7,  NULL, 0, '03',   '建安工程费',     1, 0, '刚性', 3),
(8,  NULL, 7, '0301', '土方及基坑工程', 2, 1, '刚性', 1),
(9,  NULL, 7, '0302', '主体建筑工程',   2, 1, '刚性', 2),
(10, NULL, 7, '0303', '主体安装工程',   2, 1, '刚性', 3),
(11, NULL, 7, '0304', '消防工程',       2, 1, '刚性', 4),
(12, NULL, 0, '04',   '基础设施费',     1, 0, '预警', 4),
(13, NULL, 12, '0401', '室外管网工程',   2, 1, '预警', 1),
(14, NULL, 12, '0402', '园林绿化工程',   2, 1, '预警', 2),
(15, NULL, 0, '05',   '公共配套设施费', 1, 0, '预警', 5),
(16, NULL, 0, '06',   '开发间接费用',   1, 0, '预警', 6);

-- ========== 种子：分类字典 ==========
INSERT INTO mdm_category (category_type, parent_id, code, name) VALUES
('CONTRACT', 0, 'GC',  '工程总包类'),
('CONTRACT', 0, 'ZC',  '专业分包类'),
('CONTRACT', 0, 'CL',  '材料设备类'),
('CONTRACT', 0, 'FW',  '咨询服务类'),
('PURCHASE', 0, 'GCTB', '工程-总包'),
('PURCHASE', 0, 'GYFB', '工程-专业分包'),
('PURCHASE', 0, 'CLSB', '材料设备'),
('PURCHASE', 0, 'FWZX', '服务咨询'),
('CHANGE',   0, 'SJBG', '设计变更'),
('CHANGE',   0, 'GCQZ', '工程签证'),
('CHANGE',   0, 'QSSY', '洽商事宜'),
('EXPENSE',  0, 'GLFY', '管理费用'),
('EXPENSE',  0, 'XSFY', '销售费用'),
('EXPENSE',  0, 'CWFY', '财务费用');

INSERT INTO mdm_dict (dict_key, label, value, sort) VALUES
('project.status', '筹备', 'PREPARING', 1),
('project.status', '在建', 'CONSTRUCTION', 2),
('project.status', '交付', 'DELIVERED', 3),
('project.status', '清算', 'LIQUIDATION', 4),
('supplier.grade', 'A级', 'A', 1),
('supplier.grade', 'B级', 'B', 2),
('supplier.grade', 'C级', 'C', 3),
('supplier.grade', 'D级', 'D', 4),
('procurement.method', '公开招标', 'PUBLIC_BID', 1),
('procurement.method', '邀请招标', 'INVITE_BID', 2),
('procurement.method', '竞争性谈判', 'COMPETITIVE', 3),
('procurement.method', '询比价', 'INQUIRY', 4),
('procurement.method', '直接委托', 'DIRECT', 5),
('procurement.method', '战略集采', 'FRAMEWORK', 6);
