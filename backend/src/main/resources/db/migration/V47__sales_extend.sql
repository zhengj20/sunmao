-- =====================================================
-- V47 销售管理扩展（对标明源销售补齐）：
--   客户跟进(跟进记录+客户池) / 销售目标(月度目标+达成率) / 渠道(中介/分销)
--   销售驾驶舱 / 销售总览(楼栋销控) / 置业顾问业绩 / 蓄客漏斗 —— 均为只读聚合，基于既有 sale_* 表 + 本表
-- 约定：金额 DECIMAL(16,2)；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- =====================================================

-- 1) 客户跟进记录（客户池 + 跟进轨迹 + 跟进提醒）
CREATE TABLE sale_follow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  customer_id BIGINT COMMENT '关联 mdm_customer(可空)',
  customer_name VARCHAR(64) NOT NULL COMMENT '客户姓名/手机号(判客键,冗余)',
  follow_method VARCHAR(32) COMMENT '电话/到访/微信/短信',
  follow_content VARCHAR(500) COMMENT '跟进内容',
  intent_level VARCHAR(16) COMMENT 'HIGH高/MID中/LOW低',
  next_follow_date DATE COMMENT '下次跟进时间',
  deal_status VARCHAR(16) DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING跟进中/DEAL已成交/LOST已流失',
  follow_by BIGINT COMMENT '置业顾问',
  follow_at DATETIME COMMENT '跟进时间',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_follow_project (project_id),
  KEY idx_follow_customer (project_id, customer_id),
  KEY idx_follow_next (deal_status, next_follow_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户跟进记录';

-- 2) 销售目标（月度/季度/年度 + 金额/套数/面积口径，可细分到顾问）
CREATE TABLE sale_target (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  period_type VARCHAR(16) NOT NULL COMMENT 'MONTH月/QUARTER季/YEAR年',
  period VARCHAR(16) NOT NULL COMMENT '周期(2026-09 / 2026-Q3 / 2026)',
  metric VARCHAR(16) NOT NULL DEFAULT 'AMOUNT' COMMENT 'AMOUNT金额/COUNT套数/AREA面积',
  target_value DECIMAL(16,2) NOT NULL COMMENT '目标值',
  agent_id BIGINT COMMENT '细分到置业顾问(空=项目整体)',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE生效/INACTIVE停用',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_target (project_id, period, metric, agent_id),
  KEY idx_target_project (project_id, period_type, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售目标';

-- 3) 渠道（中介/分销/线上/老带新/自然 + 佣金率）
CREATE TABLE sale_channel (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  channel_name VARCHAR(128) NOT NULL COMMENT '渠道名称',
  channel_type VARCHAR(32) COMMENT '中介/分销/线上/老带新/自然来访',
  contact_name VARCHAR(64) COMMENT '对接人',
  commission_rate DECIMAL(5,4) COMMENT '佣金率(0~1)',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE生效/INACTIVE停用',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_channel (project_id, channel_name),
  KEY idx_channel_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售渠道';

-- =====================================================
-- 系统参数（跟进提醒阈值）
-- =====================================================
INSERT INTO sys_param (param_key, param_value, name, remark) VALUES
('sales.follow.overdue_days', '3', '跟进逾期阈值(天)', '下次跟进时间超过N天未跟进即视为逾期预警');

-- =====================================================
-- RBAC：菜单（销售管理 id=11 下新增）+ 角色授权
-- 118驾驶舱 119总览 120客户跟进 121销售目标 122渠道 123顾问业绩 124蓄客漏斗
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(118, 11, '销售驾驶舱',   'M', '/sales/cockpit',   'sales/cockpit/index',   'DataAnalysis', 0),
(119, 11, '销售总览',     'M', '/sales/overview',  'sales/overview/index',  'Odometer',     1),
(120, 11, '客户跟进',     'M', '/sales/follow',    'sales/follow/index',    'ChatDotRound', 8),
(121, 11, '销售目标',     'M', '/sales/target',    'sales/target/index',    'Aim',          9),
(122, 11, '渠道管理',     'M', '/sales/channel',   'sales/channel/index',   'Connection',   10),
(123, 11, '置业顾问业绩', 'M', '/sales/advisor',   'sales/advisor/index',   'Medal',        11),
(124, 11, '蓄客漏斗',     'M', '/sales/funnel',    'sales/funnel/index',    'TrendCharts',  12);

-- 授权：集团管理员(1) 全部；销售经理(6) 全部销售菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 118), (1, 119), (1, 120), (1, 121), (1, 122), (1, 123), (1, 124),
(6, 118), (6, 119), (6, 120), (6, 121), (6, 122), (6, 123), (6, 124);

-- =====================================================
-- 种子数据（项目1，确保驾驶舱/总览/目标/渠道/顾问/漏斗出数）
-- =====================================================
-- 渠道（项目1，含自然来访 + 中介）
INSERT INTO sale_channel (project_id, channel_name, channel_type, contact_name, commission_rate, status, created_at, updated_at) VALUES
(1, '自然来访',   '自然来访', NULL,            0.0000, 'ACTIVE', NOW(), NOW()),
(1, '老带新',     '老带新',   NULL,            0.0100, 'ACTIVE', NOW(), NOW()),
(1, '安居客中介', '中介',     '王渠道',        0.0250, 'ACTIVE', NOW(), NOW()),
(1, '分销渠道A',  '分销',     '李分销',        0.0300, 'ACTIVE', NOW(), NOW());

-- 销售目标（项目1，2026-09 月度：金额/套数）
INSERT INTO sale_target (project_id, period_type, period, metric, target_value, agent_id, status, created_at, updated_at) VALUES
(1, 'MONTH',  '2026-09',  'AMOUNT', 80000000.00, NULL, 'ACTIVE', NOW(), NOW()),
(1, 'MONTH',  '2026-09',  'COUNT',  20.00,       NULL, 'ACTIVE', NOW(), NOW());

-- 客户跟进（项目1，覆盖跟进中/已成交/流失，含逾期跟进）
INSERT INTO sale_follow (project_id, customer_id, customer_name, follow_method, follow_content, intent_level, next_follow_date, deal_status, follow_by, follow_at, created_at, updated_at) VALUES
(1, 1, '13800001001', '到访', '带看1#-002，客户关注户型，约下周复看', 'HIGH', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'FOLLOWING', 2, '2026-09-03 15:00:00', NOW(), NOW()),
(1, 2, '13800001002', '电话', '已签约1#-002，跟进按揭材料提交进度',  'HIGH', DATE_SUB(CURDATE(), INTERVAL 1 DAY),   'DEAL',      2, '2026-09-02 10:30:00', NOW(), NOW()),
(1, NULL,'13800001003','微信', '价格敏感，待渠道带客复联',            'MID',  DATE_SUB(CURDATE(), INTERVAL 5 DAY),   'FOLLOWING', 2, '2026-09-01 14:20:00', NOW(), NOW()),
(1, NULL,'13800001004','电话', '预算不足，转竞品，标记流失',           'LOW',  NULL,                                    'LOST',      2, '2026-08-28 16:10:00', NOW(), NOW());
