-- =====================================================
-- V24 指标字典补种：成本偏差率 / 价格实现率 / 招采阳光度
--   在 V23 的 metric_def 基础上补齐三级驾驶舱所需口径（INSERT IGNORE 幂等）
-- =====================================================

INSERT IGNORE INTO metric_def
  (metric_code, metric_name, formula, source_table, dimension, owner, status, effective_date, created_at, updated_at) VALUES
('COST_DEVIATION_RATE', '成本偏差率', '(动态成本-目标成本)/目标成本×100%（最新月度快照）', 'cost_dynamic_snapshot', '集团/项目/月度', '张成', 'ACTIVE', CURDATE(), NOW(), NOW()),
('PRICE_ACHIEVEMENT',   '价格实现率', '成交均价(sale_contract)/表价均价(sale_room_status.table_price)×100%', 'sale_contract+sale_room_status', '集团/项目', '钱销售', 'ACTIVE', CURDATE(), NOW(), NOW()),
('PROC_SUNSHINE',       '招采阳光度', '公开招标立项数/总立项数×100%（附定标/立项金额比）', 'proc_project+proc_award', '集团/区域/项目', '李采', 'ACTIVE', CURDATE(), NOW(), NOW());
