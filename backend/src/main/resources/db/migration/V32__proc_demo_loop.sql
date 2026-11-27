-- =====================================================
-- V32 招采闭环演示数据：多条招采覆盖全阶段/多方式/闭环到合同/超预算/超期/围串标
-- （在 V8 已有的 project 1=机电安装[评标中] 基础上补充 project 2-5）
-- =====================================================

-- ---------- Project 2：幕墙玻璃工程 —— 公开招标，已闭环到合同（CLOSED），定标低于预算 ----------
INSERT INTO proc_project (id, project_code, project_id, title, budget_amount, plan_item_id, fund_source, procure_method, period_days, status, created_at) VALUES
(2, 'ZC-202606-0002', 1, '星汇湾二期幕墙玻璃工程', 22000000.00, NULL, '自有资金', 'PUBLIC_BID', 120, 'CLOSED', '2026-05-06 09:00:00');

INSERT INTO proc_entry (proc_project_id, supplier_id, supplier_name, precheck_result, inspection_score, inspection_conclusion, is_candidate, status, created_at) VALUES
(2, 1, '钱江建工集团股份有限公司', 'PASSED', 90.00, 'GOOD', 1, 'APPROVED', '2026-05-08 09:00:00'),
(2, 2, '之江建设集团有限公司', 'PASSED', 92.00, 'GOOD', 1, 'APPROVED', '2026-05-08 09:00:00');

INSERT INTO proc_tender (id, tender_code, proc_project_id, doc_version, deposit_amount, publish_time, deadline, status, created_at) VALUES
(2, 'FB-202606-0002', 2, 'V1.0', 400000.00, '2026-05-10 10:00:00', '2026-05-20 17:00:00', 'CLOSED', '2026-05-08 09:00:00');

INSERT INTO proc_bid (id, tender_id, proc_project_id, supplier_id, supplier_name, bid_time, price_amount, bid_ip, compliance_result, bid_status, created_at) VALUES
(10, 2, 2, 1, '钱江建工集团股份有限公司', '2026-05-20 15:00:00', 21800000.00, '203.0.113.21', 'PASSED', 'OPENED', '2026-05-20 15:00:00'),
(11, 2, 2, 2, '之江建设集团有限公司', '2026-05-20 15:20:00', 20500000.00, '203.0.113.22', 'PASSED', 'OPENED', '2026-05-20 15:20:00'),
(12, 2, 2, 6, '甬城市政建设集团股份有限公司', '2026-05-20 15:40:00', 22600000.00, '198.51.100.9', 'PASSED', 'OPENED', '2026-05-20 15:40:00');

INSERT INTO proc_evaluation (id, proc_project_id, method, tech_weight, biz_weight, expert_names, status, conclusion, created_at) VALUES
(2, 2, 'COMPREHENSIVE', 60.00, 40.00, '专家:王工/陈工/赵工', 'REPORTED', '综合评分第一：之江建设', '2026-05-25 10:00:00');

INSERT INTO proc_award (id, award_code, proc_project_id, winner_supplier_id, winner_supplier_name, award_amount, budget_amount, deviation, deviation_pct, control_check, reason, notice_status, contract_generated, status, created_at) VALUES
(2, 'DB-202606-0002', 2, 2, '之江建设集团有限公司', 20500000.00, 22000000.00, -1500000.00, -6.82, 'PASSED', '综合评分第一且报价最低', 'SENT', 1, 'APPROVED', '2026-06-20 10:00:00');

-- ---------- Project 3：电梯设备采购 —— 邀请招标，已定标（AWARDED），定标超预算 +10%，围串标预警 ----------
INSERT INTO proc_project (id, project_code, project_id, title, budget_amount, plan_item_id, fund_source, procure_method, period_days, status, created_at) VALUES
(3, 'ZC-202607-0003', 2, '星光里电梯设备采购', 9000000.00, NULL, '自有资金', 'INVITE_BID', 90, 'AWARDED', '2026-06-25 09:00:00');

INSERT INTO proc_entry (proc_project_id, supplier_id, supplier_name, precheck_result, inspection_score, inspection_conclusion, is_candidate, status, created_at) VALUES
(3, 8, '恒信建材贸易有限公司', 'PASSED', 86.00, 'GOOD', 1, 'APPROVED', '2026-06-28 09:00:00'),
(3, 9, '浙江鉴衡工程检测有限公司', 'PASSED', 80.00, 'PASS', 1, 'APPROVED', '2026-06-28 09:00:00');

INSERT INTO proc_tender (id, tender_code, proc_project_id, doc_version, deposit_amount, publish_time, deadline, status, created_at) VALUES
(3, 'FB-202607-0003', 3, 'V1.0', 200000.00, '2026-07-01 10:00:00', '2026-07-12 17:00:00', 'CLOSED', '2026-06-28 09:00:00');

INSERT INTO proc_bid (id, tender_id, proc_project_id, supplier_id, supplier_name, bid_time, price_amount, bid_ip, compliance_result, bid_status, created_at) VALUES
(13, 3, 3, 8, '恒信建材贸易有限公司', '2026-07-12 15:00:00', 9900000.00, '203.0.113.31', 'PASSED', 'OPENED', '2026-07-12 15:00:00'),
(14, 3, 3, 9, '浙江鉴衡工程检测有限公司', '2026-07-12 15:15:00', 9750000.00, '203.0.113.31', 'PASSED', 'OPENED', '2026-07-12 15:15:00');

INSERT INTO proc_evaluation (id, proc_project_id, method, tech_weight, biz_weight, expert_names, status, conclusion, created_at) VALUES
(3, 3, 'COMPREHENSIVE', 50.00, 50.00, '专家:刘工/杨工', 'REPORTED', '综合评分第一：恒信建材', '2026-07-15 10:00:00');

INSERT INTO proc_award (id, award_code, proc_project_id, winner_supplier_id, winner_supplier_name, award_amount, budget_amount, deviation, deviation_pct, control_check, reason, notice_status, contract_generated, status, created_at) VALUES
(3, 'DB-202607-0003', 3, 8, '恒信建材贸易有限公司', 9900000.00, 9000000.00, 900000.00, 10.00, 'WARN', '技术分领先，报价略超预算', 'SENT', 0, 'APPROVED', '2026-08-15 10:00:00');

INSERT INTO proc_collusion_warn (proc_project_id, rule_code, detail, risk_level, created_at) VALUES
(3, 'SAME_IP', '恒信建材与浙江鉴衡回标IP相同(203.0.113.31)，存在围串标嫌疑', 'MEDIUM', '2026-07-12 16:00:00');

-- ---------- Project 4：消防工程采购 —— 竞争性谈判，发标中（TENDERING），停滞 70+ 天（超期） ----------
INSERT INTO proc_project (id, project_code, project_id, title, budget_amount, plan_item_id, fund_source, procure_method, period_days, status, created_at) VALUES
(4, 'ZC-202605-0004', 1, '星汇湾一期消防工程', 6500000.00, NULL, '自有资金', 'COMPETITIVE', 60, 'TENDERING', '2026-06-10 09:00:00');

INSERT INTO proc_entry (proc_project_id, supplier_id, supplier_name, precheck_result, inspection_score, inspection_conclusion, is_candidate, status, created_at) VALUES
(4, 1, '钱江建工集团股份有限公司', 'PASSED', 84.00, 'GOOD', 1, 'APPROVED', '2026-06-15 09:00:00'),
(4, 6, '甬城市政建设集团股份有限公司', 'PASSED', 82.00, 'PASS', 1, 'APPROVED', '2026-06-15 09:00:00');

INSERT INTO proc_tender (id, tender_code, proc_project_id, doc_version, deposit_amount, publish_time, deadline, status, created_at) VALUES
(4, 'FB-202605-0004', 4, 'V1.0', 150000.00, '2026-06-20 10:00:00', '2026-07-05 17:00:00', 'OPENED', '2026-06-18 09:00:00');

-- ---------- Project 5：智能化系统采购 —— 询比价，立项审批中（APPROVING），近期新建（不超期） ----------
INSERT INTO proc_project (id, project_code, project_id, title, budget_amount, plan_item_id, fund_source, procure_method, period_days, status, created_at) VALUES
(5, 'ZC-202609-0005', 2, '星光里智能化系统', 3500000.00, NULL, '自有资金', 'INQUIRY', 45, 'APPROVING', '2026-09-04 09:00:00');
