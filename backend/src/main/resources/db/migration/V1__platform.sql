-- =====================================================
-- V1 平台底座：系统管理 + 事件Outbox + 全模块菜单/账号/参数种子
-- =====================================================

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  password VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
  real_name VARCHAR(64) COMMENT '姓名',
  mobile VARCHAR(32) COMMENT '手机号',
  email VARCHAR(128) COMMENT '邮箱',
  org_id BIGINT COMMENT '组织ID',
  job_level VARCHAR(32) COMMENT '职务级别(M1-M6/E1-E9)',
  status TINYINT DEFAULT 1 COMMENT '1启用 0冻结',
  last_login_at DATETIME,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '角色名',
  code VARCHAR(64) NOT NULL COMMENT '角色编码',
  remark VARCHAR(255),
  status TINYINT DEFAULT 1,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_user_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';

CREATE TABLE sys_role_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单';

CREATE TABLE sys_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT 0 COMMENT '父菜单',
  name VARCHAR(64) NOT NULL,
  type CHAR(1) DEFAULT 'M' COMMENT 'D目录 M菜单 B按钮',
  path VARCHAR(128) COMMENT '路由路径',
  component VARCHAR(128) COMMENT '组件路径',
  perm_code VARCHAR(128) COMMENT '权限码',
  icon VARCHAR(64),
  sort INT DEFAULT 0,
  visible TINYINT DEFAULT 1,
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单';

CREATE TABLE sys_param (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  param_key VARCHAR(64) NOT NULL,
  param_value VARCHAR(512),
  name VARCHAR(128),
  remark VARCHAR(255),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_param_key (param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

CREATE TABLE sys_op_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT, username VARCHAR(64),
  module VARCHAR(64), action VARCHAR(64), biz_id VARCHAR(64),
  detail VARCHAR(1000), ip VARCHAR(64), created_at DATETIME,
  KEY idx_oplog_module (module), KEY idx_oplog_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

CREATE TABLE sys_login_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT, username VARCHAR(64), ip VARCHAR(64),
  success TINYINT, msg VARCHAR(128), created_at DATETIME,
  KEY idx_login_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE biz_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  topic VARCHAR(128) NOT NULL COMMENT '{domain}.{object}.{action}',
  biz_type VARCHAR(64), biz_id BIGINT,
  payload JSON,
  status VARCHAR(16) DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/FAILED',
  error_msg VARCHAR(512),
  created_at DATETIME,
  KEY idx_event_topic (topic), KEY idx_event_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领域事件Outbox';

-- ========== 菜单种子（一期全模块） ==========
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(1,  0, '工作台',   'M', '/workbench', 'portal/workbench/index', 'HomeFilled', 1),
(2,  0, '主数据',   'D', '/mdm',       NULL, 'Folder', 10),
(21, 2, '组织机构', 'M', '/mdm/org',        'mdm/org/index',        '', 1),
(22, 2, '员工管理', 'M', '/mdm/employee',   'mdm/employee/index',   '', 2),
(23, 2, '项目管理', 'M', '/mdm/project',    'mdm/project/index',    '', 3),
(24, 2, '成本科目', 'M', '/mdm/account',    'mdm/account/index',    '', 4),
(25, 2, '分类字典', 'M', '/mdm/category',   'mdm/category/index',   '', 5),
(26, 2, '供应商库', 'M', '/mdm/supplier',   'mdm/supplier/index',   '', 6),
(27, 2, '客户管理', 'M', '/mdm/customer',   'mdm/customer/index',   '', 7),
(28, 2, '编码规则', 'M', '/mdm/coderule',   'mdm/coderule/index',   '', 8),
(3,  0, '招采管理', 'D', '/proc',      NULL, 'ShoppingCart', 20),
(31, 3, '采购需求', 'M', '/proc/requirement', 'proc/requirement/index', '', 1),
(32, 3, '采购计划', 'M', '/proc/plan',        'proc/plan/index',        '', 2),
(33, 3, '招采立项', 'M', '/proc/project',     'proc/project/index',     '', 3),
(34, 3, '入围与考察', 'M', '/proc/entry',     'proc/entry/index',       '', 4),
(35, 3, '发标答疑', 'M', '/proc/tender',      'proc/tender/index',      '', 5),
(36, 3, '回标管理', 'M', '/proc/bid',         'proc/bid/index',         '', 6),
(37, 3, '评标管理', 'M', '/proc/evaluation',  'proc/evaluation/index',  '', 7),
(38, 3, '定标管理', 'M', '/proc/award',       'proc/award/index',       '', 8),
(39, 3, '直接委托', 'M', '/proc/direct',      'proc/direct/index',      '', 9),
(40, 3, '战略集采', 'M', '/proc/framework',   'proc/framework/index',   '', 10),
(41, 3, '采购台账', 'M', '/proc/ledger',      'proc/ledger/index',      '', 11),
(4,  0, '成本管理', 'D', '/cost',      NULL, 'Coin', 30),
(51, 4, '目标成本', 'M', '/cost/target',   'cost/target/index',   '', 1),
(52, 4, '合约规划', 'M', '/cost/plan',     'cost/plan/index',     '', 2),
(53, 4, '合同管理', 'M', '/cost/contract', 'cost/contract/index', '', 3),
(54, 4, '变更管理', 'M', '/cost/change',   'cost/change/index',   '', 4),
(55, 4, '付款管理', 'M', '/cost/payment',  'cost/payment/index',  '', 5),
(56, 4, '动态成本', 'M', '/cost/dynamic',  'cost/dynamic/index',  '', 6),
(57, 4, '无效成本', 'M', '/cost/invalid',  'cost/invalid/index',  '', 7),
(58, 4, '成本指标库', 'M', '/cost/indicator', 'cost/indicator/index', '', 8),
(5,  0, '投资货值', 'D', '/invest',    NULL, 'TrendCharts', 40),
(61, 5, '投资测算', 'M', '/invest/calc',   'invest/calc/index',   '', 1),
(62, 5, '货值台账', 'M', '/invest/value',  'invest/value/index',  '', 2),
(63, 5, '动态利润', 'M', '/invest/profit', 'invest/profit/index', '', 3),
(6,  0, '资金管理', 'D', '/fund',      NULL, 'Money', 50),
(71, 6, '资金计划', 'M', '/fund/plan',    'fund/plan/index',    '', 1),
(72, 6, '收付执行', 'M', '/fund/execute', 'fund/execute/index', '', 2),
(73, 6, '融资台账', 'M', '/fund/finance',  'fund/finance/index', '', 3),
(7,  0, '审批中心', 'D', '/workflow',  NULL, 'Stamp', 60),
(81, 7, '待办任务', 'M', '/workflow/todo',    'workflow/todo/index',    '', 1),
(82, 7, '已办任务', 'M', '/workflow/done',    'workflow/done/index',    '', 2),
(83, 7, '我发起的', 'M', '/workflow/mine',    'workflow/mine/index',    '', 3),
(84, 7, '流程设计', 'M', '/workflow/design',  'workflow/design/index',  '', 4),
(85, 7, '流程监控', 'M', '/workflow/monitor', 'workflow/monitor/index', '', 5),
(8,  0, '驾驶舱',   'M', '/cockpit', 'cockpit/index', 'DataLine', 5),
(9,  0, '系统管理', 'D', '/sys',      NULL, 'Setting', 90),
(91, 9, '用户管理', 'M', '/sys/user',  'sys/user/index',  '', 1),
(92, 9, '角色权限', 'M', '/sys/role',  'sys/role/index',  '', 2),
(93, 9, '菜单管理', 'M', '/sys/menu',  'sys/menu/index',  '', 3),
(94, 9, '系统参数', 'M', '/sys/param', 'sys/param/index', '', 4),
(95, 9, '操作日志', 'M', '/sys/log',   'sys/log/index',   '', 5),
(10, 0, '集成平台', 'D', '/int',      NULL, 'Connection', 95),
(101, 10, 'API注册',   'M', '/int/api',     'int/api/index',     '', 1),
(102, 10, '适配器配置', 'M', '/int/adapter', 'int/adapter/index', '', 2),
(103, 10, '调用日志', 'M', '/int/log',      'int/log/index',     '', 3);

-- ========== 角色 ==========
INSERT INTO sys_role (id, name, code, remark) VALUES
(1, '集团管理员', 'ADMIN',    '全部权限'),
(2, '成本经理',   'COST_MGR', '成本/招采/主数据/投资/资金/驾驶舱/审批'),
(3, '招采经理',   'PROC_MGR', '招采/主数据/审批'),
(4, '财务经理',   'FIN_MGR',  '资金/投资/付款/驾驶舱'),
(5, '观察者',     'VIEWER',   '只看工作台与驾驶舱');

-- admin 拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
-- 成本经理：业务菜单（除系统管理/集成平台）
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE parent_id NOT IN (9, 10) AND id NOT IN (9, 10);
-- 招采经理：主数据+招采+审批+工作台
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 3, id FROM sys_menu WHERE id IN (1, 2, 21, 22, 23, 24, 25, 26, 28, 3, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 7, 81, 82, 83);
-- 财务经理：资金/投资/成本付款/驾驶舱/工作台/审批
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 4, id FROM sys_menu WHERE id IN (1, 5, 61, 62, 63, 6, 71, 72, 73, 4, 55, 56, 8, 7, 81, 82, 83);
-- 观察者
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (5, 1), (5, 8);

-- ========== 账号（首次登录自动升级BCrypt） ==========
INSERT INTO sys_user (id, username, password, real_name, mobile, status, org_id, job_level) VALUES
(1, 'admin',   'PLAIN:admin123', '系统管理员', '13800000001', 1, 1, 'M5'),
(2, 'costmgr', 'PLAIN:123456',   '张成',       '13800000002', 1, 4, 'M3'),
(3, 'procmgr', 'PLAIN:123456',   '李采',       '13800000003', 1, 4, 'M3'),
(4, 'finmgr',  'PLAIN:123456',   '王财',       '13800000004', 1, 5, 'M4'),
(5, 'viewer',  'PLAIN:123456',   '赵观',       '13800000005', 1, 1, 'E5');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2), (3, 3), (4, 4), (5, 5);

-- ========== 系统参数（业务规则阈值，方案第16章：规则配置化） ==========
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('proc.award.min_entry_count',   '3',        '招标最少入围家数', '不足则阻断'),
('proc.award.min_bid_count',     '3',        '询比价最少报价家数', '不足则阻断'),
('proc.public.bid.threshold',    '5000000',  '公开招标金额门槛(元)', '≥该金额建议公开招标'),
('cost.change.warn_rate',        '5',        '变更率预警阈值(%)', '确认变更累计超合同额比例'),
('cost.plan.over_policy',        'WARN',     '合约规划超余量策略', 'BLOCK阻断 / WARN预警+升级审批'),
('cost.pay.invoice_required',    'true',     '付款需收票校验', '进度款申请必须关联发票'),
('fund.large.pay.threshold',     '1000000',  '大额付款标记阈值(元)', '资金监控高亮'),
('wfl.approve.timeout.hours',    '48',       '审批节点超时时长(小时)', '超时提醒'),
('reip.currency.scale',          '2',        '金额小数位', '全局金额精度');
