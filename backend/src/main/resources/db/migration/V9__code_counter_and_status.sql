-- =====================================================
-- V9 修复：编码计数器种子 + 变更单状态列宽
--   1) V8 演示数据直接 INSERT 了业务单，但未通过编码引擎累加计数器，
--      导致同项目再建第二单时序号从 1 重新生成、撞 -001 唯一键。
--      → 按各表现有单量初始化计数器（幂等：只增不减，重跑安全）
--   2) cost_change.status 原为 VARCHAR(16)，放不下 ESTIMATE_APPROVING(18字符)，
--      两阶段变更“提交估价”会报 Data too long → 扩到 VARCHAR(32)
-- =====================================================

-- 1) 编码计数器种子（dim_key 与 CodeRuleService 的 YEAR 维度一致 = 当前年份）
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no)
SELECT 'MB',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM cost_target_version)
UNION ALL SELECT 'HYGH', DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM cost_plan)
UNION ALL SELECT 'HT',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM cost_contract)
UNION ALL SELECT 'BG',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM cost_change)
UNION ALL SELECT 'FK',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM cost_payment_apply)
UNION ALL SELECT 'PRJ',  DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM mdm_project)
UNION ALL SELECT 'GYS',  DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM mdm_supplier)
UNION ALL SELECT 'KH',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM mdm_customer)
UNION ALL SELECT 'CGXQ', DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM proc_requirement)
UNION ALL SELECT 'CGXM', DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM proc_project)
UNION ALL SELECT 'FB',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM proc_tender)
UNION ALL SELECT 'DB',   DATE_FORMAT(CURDATE(), '%Y'), (SELECT COUNT(*) FROM proc_award)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));

-- 2) 变更单状态列宽（ESTIMATE_APPROVING 为 18 字符）
ALTER TABLE cost_change
  MODIFY COLUMN status VARCHAR(32) DEFAULT 'ESTIMATING'
  COMMENT 'ESTIMATING估价中/ESTIMATE_APPROVING/CONFIRMING待确认/APPROVING确认审批中/CONFIRMED已确认/REJECTED';
