-- =====================================================
-- V23 驾驶舱二期：指标字典(metric_def) + 三级驾驶舱菜单种子
--   新增唯一业务表 metric_def（指标字典，口径留痕）
--   其余均为跨域只读聚合（sale/cost/expense/plan/proc/invest），不新建业务表
-- 约定：金额 DECIMAL；每表含 version(乐观锁)/deleted(逻辑删除)/created_at/updated_at
-- =====================================================

-- 1) 指标字典（口径公式 + 来源表 + 负责人 + 维度 + 生效日期 + 版本留痕）
CREATE TABLE metric_def (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  metric_code VARCHAR(64) NOT NULL COMMENT '指标编码(唯一业务键)',
  metric_name VARCHAR(128) NOT NULL COMMENT '指标名称',
  formula VARCHAR(500) COMMENT '口径公式文本',
  source_table VARCHAR(128) COMMENT '来源表(主数据源)',
  dimension VARCHAR(32) COMMENT '维度:集团/区域/项目/月度',
  owner VARCHAR(64) COMMENT '负责人',
  status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE当前/HISTORY旧版本(口径变更留痕)',
  effective_date DATE COMMENT '口径生效日期',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_metric_code (metric_code),
  KEY idx_metric_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标字典';

-- =====================================================
-- 2) 种子：核心指标登记（口径公式 + 来源表 + 负责人 + 维度）
--    负责人按域归口：销售/货值=钱销售，成本=张成，费用=王财，计划=周计划，招采=李采，投资=王财
-- =====================================================
INSERT INTO metric_def (metric_code, metric_name, formula, source_table, dimension, owner, status, effective_date, created_at, updated_at) VALUES
('SIGN_AMOUNT',     '签约额',     'Σ sale_contract.contract_price（status IN ACTIVE/FILED）', 'sale_contract',      '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('RECEIPT_AMOUNT',  '回款额',     'Σ sale_receipt.received_amount（关联有效合同）',         'sale_receipt',       '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('RECEIPT_RATE',    '回款率',     'Σ回款额 / Σ应收(sale_payment_plan.plan_amount) × 100%',  'sale_receipt+sale_payment_plan', '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('TOTAL_VALUE',     '货值',       'Σ inv_value.total_value（供货货值）',                    'inv_value',          '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('TURNOVER_RATE',   '去化率',     '已售货值 / 总货值 × 100%（一房一档 status IN 认购/签约/网签）', 'sale_room_status',  '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('DYNAMIC_COST',    '动态成本',   '最新月度 cost_dynamic_snapshot.dynamic_total',            'cost_dynamic_snapshot', '集团/项目/月度', '张成', 'ACTIVE', CURDATE(), NOW(), NOW()),
('DYNAMIC_PROFIT',  '动态利润',   '动态货值 - 动态成本 - 三费 - 税金（月度快照）',          'inv_dynamic_profit',   '集团/项目/月度', '王财', 'ACTIVE', CURDATE(), NOW(), NOW()),
('CHANGE_RATE',     '变更率',     'Σ确认变更(cost_change.confirmed_amount) / Σ合同额 × 100%','cost_change+cost_contract', '集团/项目', '张成', 'ACTIVE', CURDATE(), NOW(), NOW()),
('BUDGET_EXEC_RATE','预算执行率', '(占用+核销) / 预算 × 100%（exp_budget 四栏）',           'exp_budget',         '集团',      '王财', 'ACTIVE', CURDATE(), NOW(), NOW()),
('THREE_FEE_RATIO', '三费占比',   '三费(销售+财务+管理) / 总费用 × 100%',                   'exp_reimburse_item',   '集团',      '王财', 'ACTIVE', CURDATE(), NOW(), NOW()),
('PLAN_ACHIEVE_RATE','计划达成率','已完成节点 / 总节点 × 100%（plan_task.status=COMPLETED）','plan_task',          '集团/项目', '周计划', 'ACTIVE', CURDATE(), NOW(), NOW()),
('COST_EFFICIENCY', '费效比',     '营销费 / 签约额 × 100%',                                 'exp_reimburse_item+sale_contract', '集团/项目', '王财', 'ACTIVE', CURDATE(), NOW(), NOW());

-- =====================================================
-- 3) 菜单：现有"驾驶舱"(id=8) 升级为目录，挂 5 个二级子菜单 + 保留一期经营总览
--    子菜单 id 200-205（全局未占用）
-- =====================================================
-- 3.1 将一期单页菜单(8)转为目录，承载二级子菜单
UPDATE sys_menu SET type = 'D', component = NULL WHERE id = 8;

-- 3.2 新增二级子菜单（集团/区域/项目/专题/指标字典 + 一期经营总览）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(200, 8, '经营总览',   'M', '/cockpit/overview', 'cockpit/overview/index', '', 0),
(201, 8, '集团驾驶舱', 'M', '/cockpit/group',    'cockpit/group/index',    '', 1),
(202, 8, '区域驾驶舱', 'M', '/cockpit/region',   'cockpit/region/index',   '', 2),
(203, 8, '项目驾驶舱', 'M', '/cockpit/project',  'cockpit/project/index',  '', 3),
(204, 8, '专题分析',   'M', '/cockpit/topic',    'cockpit/topic/index',    '', 4),
(205, 8, '指标字典',   'M', '/cockpit/metric',   'cockpit/metric/index',   '', 5);

-- 3.3 授权：admin(1) 全量；并同步授予一期已持有驾驶舱(8)的角色(2成本/4财务/5观察)保持侧栏可用
--     INSERT IGNORE 保证幂等（sys_role_menu 唯一键 role_id+menu_id）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r, m FROM (SELECT 1 r UNION SELECT 2 UNION SELECT 4 UNION SELECT 5) rr
CROSS JOIN (SELECT 200 m UNION SELECT 201 UNION SELECT 202 UNION SELECT 203 UNION SELECT 204 UNION SELECT 205) mm;
