-- =====================================================
-- V35 补充协议：编码规则种子 + 演示数据补全
--   1) V29 建了 cost_supplement 表，CostSupplementService 用 BC 规则生成编号，
--      但 BC 规则从未种子化 → POST /cost/supplements 必抛"编码规则不存在：BC"，
--      整个"补充协议"功能不可用。补 BC 编码规则 + 计数器。
--   2) V8 演示数据未含补充协议，前端该页为空。补 3 条（已生效/审批中/草稿，含正负金额）。
--   3) 计数器按现有单量初始化，避免后续 API 建单时序号撞 -0001 唯一键。
-- =====================================================

-- 1) BC 编码规则（补充协议）
INSERT INTO mdm_code_rule (rule_code, name, pattern, example)
VALUES ('BC', '补充协议', 'BC-{projectCode}-{YYYY}-{seq:4}', 'BC-PJ2026001-2026-0001')
ON DUPLICATE KEY UPDATE name = VALUES(name), pattern = VALUES(pattern);

-- 2) 补充协议演示数据（关联合同 1/2，覆盖三种状态 + 正负金额）
INSERT INTO cost_supplement (supplement_code, contract_id, project_id, title, amount, supplement_date, description, status, remark, created_at) VALUES
('BC-PJ2026001-2026-0001', 2, 1, '主体总承包·设计优化增补',   3500000.00, '2026-05-18', '2#楼边户型阳台调整，涉及外立面线条与防水收口，按实结算增补', 'APPROVED',  '随变更单确认后补签', NOW()),
('BC-PJ2026001-2026-0002', 1, 1, '土方基坑·降水措施增补',      1200000.00, '2026-04-25', '基坑降水周期延长，增加两台潜水泵租赁及用电', 'APPROVING', '待成本经理复核', NOW()),
('BC-PJ2026001-2026-0003', 2, 1, '主体总承包·结算优惠扣减',   -200000.00, '2026-09-01', '结算审计核减不合理取费，优惠返还', 'DRAFT',        '待补充审计报告', NOW());

-- 3) BC 计数器（YEAR 维度，按现有单量初始化，避免与演示数据撞序号）
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no)
SELECT 'BC', DATE_FORMAT(CURDATE(), '%Y'), 3
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, 3);
