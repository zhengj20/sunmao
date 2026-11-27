-- =====================================================
-- V58 费用科目主数据（三费：管理/销售/财务，与成本科目分离）
-- 背景：原费用科目混放在 mdm_cost_account 的 88 期间费用下，
--       本次将其拆分为独立 mdm_expense_account 表，并把
--       exp_budget / exp_reimburse_item / bud_budget(EXPENSE)
--       的 account_id 从旧成本科目迁移到新费用科目。
-- =====================================================

-- 1) 建表
CREATE TABLE mdm_expense_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  KEY idx_expacc_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用科目树(三费:管理/销售/财务)';

-- 2) 种子：三费科目树（管理费用/销售费用/财务费用 + 明细科目）
--    名称含"管理/销售/财务"关键字，三费归类 LIKE 规则可命中
INSERT INTO mdm_expense_account (id, parent_id, code, name, level, leaf, control_mode, sort) VALUES
(1,  0,   'GL',   '管理费用',       1, 0, '刚性', 1),
(2,  1,   'GL01', '管理费用-差旅',     2, 1, '刚性', 1),
(3,  1,   'GL02', '管理费用-办公费',   2, 1, '预警', 2),
(4,  1,   'GL03', '管理费用-业务招待', 2, 1, '预警', 3),
(5,  1,   'GL04', '管理费用-通讯费',   2, 1, '预警', 4),
(6,  1,   'GL05', '管理费用-车辆费',   2, 1, '预警', 5),
(7,  1,   'GL06', '管理费用-会议费',   2, 1, '预警', 6),
(8,  1,   'GL07', '管理费用-咨询费',   2, 1, '预警', 7),
(9,  0,   'XS',   '销售费用',       1, 0, '预警', 2),
(10, 9,   'XS01', '销售费用-市场推广', 2, 1, '预警', 1),
(11, 9,   'XS02', '销售费用-销售佣金', 2, 1, '预警', 2),
(12, 9,   'XS03', '销售费用-渠道分销', 2, 1, '预警', 3),
(13, 9,   'XS04', '销售费用-售楼处运营', 2, 1, '预警', 4),
(14, 0,   'CW',   '财务费用',       1, 0, '刚性', 3),
(15, 14,  'CW01', '财务费用-利息',     2, 1, '刚性', 1),
(16, 14,  'CW02', '财务费用-手续费',   2, 1, '预警', 2),
(17, 14,  'CW03', '财务费用-汇兑损益', 2, 1, '预警', 3);

-- 3) 迁移：把引用旧 88 期间费用成本科目的 expense 数据切到新费用科目
--    按 name 匹配（新科目保留了同名叶子：差旅/业务招待/市场推广/利息）
-- 3.1) exp_budget
UPDATE exp_budget eb
JOIN mdm_cost_account old ON old.id = eb.account_id AND old.deleted = 0 AND old.code LIKE '88%'
JOIN mdm_expense_account new ON new.name = old.name AND new.leaf = 1 AND new.deleted = 0
SET eb.account_id = new.id, eb.account_name = new.name;

-- 3.2) exp_reimburse_item
UPDATE exp_reimburse_item ri
JOIN mdm_cost_account old ON old.id = ri.account_id AND old.deleted = 0 AND old.code LIKE '88%'
JOIN mdm_expense_account new ON new.name = old.name AND new.leaf = 1 AND new.deleted = 0
SET ri.account_id = new.id, ri.account_name = new.name;

-- 3.3) bud_budget（仅费用线 EXPENSE）
UPDATE bud_budget bb
JOIN mdm_cost_account old ON old.id = bb.account_id AND old.deleted = 0 AND old.code LIKE '88%'
JOIN mdm_expense_account new ON new.name = old.name AND new.leaf = 1 AND new.deleted = 0
SET bb.account_id = new.id, bb.account_name = new.name
WHERE bb.budget_type = 'EXPENSE';

-- 4) 清理：从成本科目树中移除 88 期间费用（已迁到费用科目）
--    仅删集团模板（project_id IS NULL），不动任何项目克隆
DELETE FROM mdm_cost_account WHERE code LIKE '88%' AND project_id IS NULL;

-- 5) 菜单：主数据下新增"费用科目"（成本科目 sort=4 之后）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(230, 2, '费用科目', 'M', '/mdm/expense-account', 'mdm/expense-account/index', '', 5);

-- 6) 授权：admin(1) + 财务经理(4) 可访问
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 230), (4, 230);
