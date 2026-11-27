-- =====================================================
-- V8 演示数据：组织/项目/房源/供应商/成本/招采/投资/资金 全链路样例
-- =====================================================

-- ========== 组织 ==========
INSERT INTO mdm_org (id, parent_id, name, code, org_type, sort, created_at) VALUES
(1, 0, '星汇集团',        'GROUP',  'GROUP',   1, NOW()),
(2, 1, '华东区域',        'EAST',   'REGION',  1, NOW()),
(3, 1, '华北区域',        'NORTH',  'REGION',  2, NOW()),
(4, 2, '杭州城市公司',    'HZ',     'CITY',    1, NOW()),
(5, 1, '集团财务部',      'FIN',    'DEPT',    3, NOW()),
(6, 1, '集团成本管理部',  'COST',   'DEPT',    4, NOW()),
(7, 1, '集团招采管理部',  'PROC',   'DEPT',    5, NOW());

-- ========== 员工 ==========
INSERT INTO mdm_employee (user_id, emp_no, real_name, org_id, position, job_level, mobile, entry_date, status, created_at) VALUES
(2, 'E0012', '张成', 6, '成本经理',   'M3', '13800000002', '2021-03-01', 'IN_SERVICE', NOW()),
(3, 'E0013', '李采', 7, '招采经理',   'M3', '13800000003', '2022-06-15', 'IN_SERVICE', NOW()),
(4, 'E0014', '王财', 5, '财务经理',   'M4', '13800000004', '2020-09-01', 'IN_SERVICE', NOW());

-- ========== 项目 ==========
INSERT INTO mdm_project (id, project_code, name, org_id, city, cooperation_mode, land_acquired_date, land_area, far_area, saleable_area, plot_ratio, land_cost, status, remark, created_at) VALUES
(1, 'PJ2026001', '星汇湾',   4, '杭州', '独资', '2025-11-20', 68500.00, 123300.00, 98640.00, 1.800, 2150000000.00, 'CONSTRUCTION', '高尚住区，一期在建', NOW()),
(2, 'PJ2026002', '星光里',  4, '南京', '合作', '2026-05-08', 42000.00,  75600.00, 60480.00, 1.800, 1280000000.00, 'PREPARING',    '合作开发，我方股比60%', NOW());

INSERT INTO mdm_phase (project_id, phase_code, name, far_area, saleable_area, status, created_at) VALUES
(1, 'P1', '星汇湾一期', 61650.00, 49320.00, 'CONSTRUCTION', NOW()),
(1, 'P2', '星汇湾二期', 61650.00, 49320.00, 'PREPARING',    NOW()),
(2, 'P1', '星光里一期', 75600.00, 60480.00, 'PREPARING',    NOW());

INSERT INTO mdm_product_type (id, project_id, name, category, saleable, far_area, saleable_area, sort, created_at) VALUES
(1, 1, '高层住宅', '住宅', 1, 43000.00, 34400.00, 1, NOW()),
(2, 1, '花园洋房', '住宅', 1, 12000.00,  9600.00, 2, NOW()),
(3, 1, '沿街商铺', '商业', 1,  4600.00,  4000.00, 3, NOW()),
(4, 1, '地下车位', '车位', 1,  2000.00,  1000.00, 4, NOW());

INSERT INTO mdm_building (id, project_id, phase_id, building_code, name, floors, units, total_area, status, created_at) VALUES
(1, 1, 1, '1#', '1号楼(高层)', 26, 2, 30800.00, 'CONSTRUCTING', NOW()),
(2, 1, 1, '2#', '2号楼(高层)', 18, 2, 21300.00, 'CONSTRUCTING', NOW());

-- 房间生成（1#：26层×2单元×3户；2#：18层×2单元×3户）
INSERT INTO mdm_room (project_id, building_id, room_code, floor_no, unit_no, room_no, layout, orientation, area, product_type_id, saleable, sale_status, created_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 156)
SELECT 1, 1,
       CONCAT('PJ2026001-1#-', LPAD(s.n, 3, '0')),
       ((s.n - 1) DIV 6) + 1,
       (((s.n - 1) DIV 3) MOD 2) + 1,
       LPAD(((s.n - 1) MOD 3) + 1, 2, '0'),
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN 'A118三房' WHEN ((s.n - 1) MOD 3) = 1 THEN 'A139四房' ELSE 'B89两房' END,
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN '南' WHEN ((s.n - 1) MOD 3) = 1 THEN '东南' ELSE '南' END,
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN 118.50 WHEN ((s.n - 1) MOD 3) = 1 THEN 139.20 ELSE 89.30 END,
       1, 1, 'INIT', NOW()
FROM seq s;

INSERT INTO mdm_room (project_id, building_id, room_code, floor_no, unit_no, room_no, layout, orientation, area, product_type_id, saleable, sale_status, created_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 108)
SELECT 1, 2,
       CONCAT('PJ2026001-2#-', LPAD(s.n, 3, '0')),
       ((s.n - 1) DIV 6) + 1,
       (((s.n - 1) DIV 3) MOD 2) + 1,
       LPAD(((s.n - 1) MOD 3) + 1, 2, '0'),
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN 'A118三房' WHEN ((s.n - 1) MOD 3) = 1 THEN 'A139四房' ELSE 'B89两房' END,
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN '南' WHEN ((s.n - 1) MOD 3) = 1 THEN '东南' ELSE '南' END,
       CASE WHEN ((s.n - 1) MOD 3) = 0 THEN 118.50 WHEN ((s.n - 1) MOD 3) = 1 THEN 139.20 ELSE 89.30 END,
       1, 1, 'INIT', NOW()
FROM seq s;

-- ========== 供应商 ==========
INSERT INTO mdm_supplier (id, supplier_code, name, short_name, credit_code, classify, grade, lib_type, legal_person, registered_capital, contact_person, contact_phone, performance_score, created_at) VALUES
(1, 'GYS20260001', '钱江建工集团股份有限公司',   '钱江建工', '91330000MA27XXXX1A', '工程', 'A', 'OFFICIAL', '陈建国', 2000000000.00, '周经理', '0571-88001001', 92.50, NOW()),
(2, 'GYS20260002', '之江建设集团有限公司',       '之江建设', '91330000MA27XXXX2B', '工程', 'A', 'OFFICIAL', '沈永良', 3000000000.00, '吴经理', '0571-88001002', 90.00, NOW()),
(3, 'GYS20260003', '杭州鑫达基础工程有限公司',   '鑫达基础', '91330100MA27XXXX3C', '工程', 'B', 'OFFICIAL', '刘鑫达', 800000000.00,  '郑经理', '0571-88001003', 85.00, NOW()),
(4, 'GYS20260004', '之江消防设备工程有限公司',   '之江消防', '91330100MA27XXXX4D', '工程', 'B', 'OFFICIAL', '孙之江', 500000000.00,  '冯经理', '0571-88001004', 83.20, NOW()),
(5, 'GYS20260005', '西溪景观工程有限公司',       '西溪景观', '91330100MA27XXXX5E', '工程', 'B', 'OFFICIAL', '李绿',   300000000.00,  '何经理', '0571-88001005', 87.00, NOW()),
(6, 'GYS20260006', '甬城市政建设集团股份有限公司','甬城市政', '91330200MA27XXXX6F', '工程', 'A', 'OFFICIAL', '王市政', 1500000000.00, '应经理', '0574-88001006', 88.50, NOW()),
(7, 'GYS20260007', '杭州云设计建筑设计有限公司', '云设计',   '91330100MA27XXXX7G', '服务', 'B', 'OFFICIAL', '张云',   50000000.00,   '林工',   '0571-88001007', 86.00, NOW()),
(8, 'GYS20260008', '恒信建材贸易有限公司',       '恒信建材', '91330100MA27XXXX8H', '材料', 'C', 'PRESELECT', '赵恒信', 100000000.00,  '钱经理', '0571-88001008', 78.00, NOW()),
(9, 'GYS20260009', '浙江鉴衡工程检测有限公司',   '鉴衡检测', '91330100MA27XXXX9I', '服务', 'C', 'PRESELECT', '孙鉴衡', 30000000.00,   '李工',   '0571-88001009', 80.00, NOW()),
(10,'GYS20260010', '杭州失信建设工程有限公司',   '失信建设', '91330100MA27XXXX0J', '工程', 'D', 'BLACKLIST', '吴失信', 50000000.00,   '吴总',   '0571-88001010', 45.00, NOW());

INSERT INTO mdm_supplier_qualification (supplier_id, qual_type, qual_no, issue_date, expire_date, created_at) VALUES
(1, '建筑工程施工总承包特级资质', 'A1330033020001', '2023-06-01', '2028-06-01', NOW()),
(1, '安全生产许可证', '浙JZ安许证字[2023]0001', '2023-01-15', '2026-09-30', NOW()),
(2, '建筑工程施工总承包特级资质', 'A1330033020002', '2022-08-01', '2027-08-01', NOW()),
(4, '消防设施工程专业承包一级', 'F1330033020004', '2024-03-01', '2029-03-01', NOW()),
(10,'建筑工程施工总承包二级', 'B1330033020010', '2021-05-01', '2026-05-01', NOW());

-- ========== 客户 ==========
INSERT INTO mdm_customer (id, customer_code, name, cert_type, cert_no, customer_type, mobile, created_at) VALUES
(1, 'KH202600001', '沈明轩', 'ID_CARD', '330100199001010011', 'PERSON', '13800001001', NOW()),
(2, 'KH202600002', '顾清瑜', 'ID_CARD', '330100199202020022', 'PERSON', '13800001002', NOW()),
(3, 'KH202600003', '杭州澜悦科技有限公司', 'BIZ_LICENSE', '91330106MA2GK3456X', 'ENTERPRISE', '0571-88990003', NOW());

-- ========== 成本科目(项目1克隆集团模板) ==========
INSERT INTO mdm_cost_account (project_id, parent_id, code, name, level, leaf, control_mode, sort, created_at)
SELECT 1, NULL, t.code, t.name, t.level, t.leaf, t.control_mode, t.sort, NOW() FROM mdm_cost_account t WHERE t.project_id IS NULL;
-- 修正克隆后的父子关系（二级挂到项目内的一级）
UPDATE mdm_cost_account c JOIN mdm_cost_account p ON c.project_id = 1 AND p.project_id = 1
  AND p.code = SUBSTRING(c.code, 1, 2) AND p.level = 1
SET c.parent_id = p.id
WHERE c.project_id = 1 AND c.level = 2;

-- ========== 目标成本(项目1 基准版 已审批) ==========
INSERT INTO cost_target_version (id, version_code, project_id, stage, total_amount, build_area, status, created_at) VALUES
(1, 'MB-PJ2026001-2026-001', 1, 'BASE', 1498000000.00, 123300.00, 'APPROVED', NOW());

INSERT INTO cost_target_item (version_id, account_id, account_code, account_name, quantity, unit, unit_price, amount, unit_cost, created_at) VALUES
(1, 1, '01', '土地获得成本',   1, '项', 2150000000.00, 0.00, 0.00, NOW()),
(1, 2, '0101', '土地出让金',   1, '项', 2050000000.00, 2050000000.00, 16626.00, NOW()),
(1, 3, '0102', '契税及费用',   1, '项', 100000000.00,  100000000.00,  811.00, NOW());
UPDATE cost_target_item SET amount = 2150000000.00 WHERE version_id = 1 AND account_code = '01';
INSERT INTO cost_target_item (version_id, account_id, account_code, account_name, quantity, unit, unit_price, amount, unit_cost, created_at) VALUES
(1, 5, '0201', '勘察设计费',   1, '项', 36800000.00,  36800000.00,  298.00, NOW()),
(1, 6, '0202', '报批报建费',   1, '项', 18200000.00,  18200000.00,  148.00, NOW()),
(1, 8, '0301', '土方及基坑工程', 68500, '㎡', 480.00,   32880000.00,  267.00, NOW()),
(1, 9, '0302', '主体建筑工程', 123300, '㎡', 3250.00,  400725000.00, 3250.00, NOW()),
(1, 10,'0303', '主体安装工程', 123300, '㎡', 1180.00,  145494000.00, 1180.00, NOW()),
(1, 11,'0304', '消防工程',     123300, '㎡', 185.00,   22810500.00,  185.00, NOW()),
(1, 13,'0401', '室外管网工程', 68500, '㎡', 260.00,   17810000.00,  144.00, NOW()),
(1, 14,'0402', '园林绿化工程', 68500, '㎡', 420.00,   28770000.00,  233.00, NOW());
-- 合计修正：目标总成本=15.7亿（含土地21.5亿外的开发成本口径演示，此处总金额仅作演示）
UPDATE cost_target_version SET total_amount = 492800000.00 WHERE id = 1;

-- ========== 合约规划(项目1 已审批) ==========
INSERT INTO cost_plan (id, plan_code, project_id, target_version_id, name, status, created_at) VALUES
(1, 'GH-PJ2026001-2026-001', 1, 1, '星汇湾一期合约规划', 'APPROVED', NOW());

INSERT INTO cost_plan_item (id, plan_id, project_id, account_id, account_code, account_name, planned_amount, contracted_amount, pending_amount, scope, expected_sign_date, created_at) VALUES
(1, 1, 1, 8,  '0301', '土方及基坑工程', 34000000.00,  32800000.00, 0, '土方开挖、支护、降水', '2026-01-10', NOW()),
(2, 1, 1, 9,  '0302', '主体建筑工程',  410000000.00, 280000000.00, 0, '总包施工(一期)',       '2026-02-01', NOW()),
(3, 1, 1, 10, '0303', '主体安装工程',  148000000.00, 0, 0, '机电安装总包',           '2026-08-01', NOW()),
(4, 1, 1, 11, '0304', '消防工程',      23500000.00,  0, 0, '消防系统安装',           '2026-09-15', NOW()),
(5, 1, 1, 13, '0401', '室外管网工程',  18200000.00,  0, 0, '雨污水电燃气管网',       '2026-11-01', NOW()),
(6, 1, 1, 14, '0402', '园林绿化工程',  29500000.00,  0, 0, '景观绿化工程',           '2027-01-15', NOW());

-- ========== 合同 ==========
INSERT INTO cost_contract (id, contract_code, project_id, plan_item_id, name, supplier_id, supplier_name, contract_type, signed_amount, signed_date, scope, start_date, end_date, retention_rate, source, status, created_at) VALUES
(1, 'HT-PJ2026001-2026-0001', 1, 1, '星汇湾一期土方及基坑工程合同', 3, '杭州鑫达基础工程有限公司', 'ZC', 32800000.00, '2026-01-18', '土方开挖、边坡支护、基坑降水', '2026-01-20', '2026-05-30', 3.00, 'MANUAL', 'EFFECTIVE', NOW()),
(2, 'HT-PJ2026001-2026-0002', 1, 2, '星汇湾一期主体建筑总承包合同', 1, '钱江建工集团股份有限公司', 'GC', 280000000.00, '2026-02-10', '1#/2#楼主体结构、二次结构、粗装修', '2026-02-15', '2027-10-30', 3.00, 'MANUAL', 'EFFECTIVE', NOW()),
(3, 'HT-PJ2026001-2026-0003', 1, 4, '星汇湾一期消防工程合同', 4, '之江消防设备工程有限公司', 'ZC', 0.00, NULL, '消防水/电/暖通联动系统', NULL, NULL, 3.00, 'MANUAL', 'DRAFT', NOW());

INSERT INTO cost_payment_plan (contract_id, term_no, term_desc, plan_amount, plan_date, paid_amount, created_at) VALUES
(1, 1, '开工预付款(10%)',   3280000.00,  '2026-01-25', 3280000.00, NOW()),
(1, 2, '土方完成进度款(60%)', 19680000.00, '2026-04-10', 12000000.00, NOW()),
(2, 1, '预付款(备料款10%)', 28000000.00,  '2026-02-20', 28000000.00, NOW()),
(2, 2, '主体至10层进度款',  56000000.00,  '2026-08-20', 0, NOW());

-- ========== 变更 ==========
INSERT INTO cost_change (id, change_code, project_id, contract_id, change_type, title, reason_category, description, estimate_amount, confirmed_amount, deduct_amount, status, require_contract, created_at) VALUES
(1, 'BG-PJ2026001-2026-0001', 1, 2, 'GCQZ', '1#楼地下室底板加厚签证', '地质条件变化', '局部遇暗塘，底板加厚300mm并加强配筋', 1850000.00, 1820000.00, 0, 'CONFIRMED', 1, NOW()),
(2, 'BG-PJ2026001-2026-0002', 1, 2, 'SJBG', '2#楼标准层户型优化设计变更', '产品优化', '边户型阳台调整，涉及外立面线条修改', 950000.00, NULL, 0, 'ESTIMATING', 1, NOW());

-- ========== 付款 ==========
INSERT INTO cost_payment_apply (id, payment_code, project_id, contract_id, payment_type, apply_amount, applied_amount, invoice_amount, description, status, approved_amount, paid_amount, paid_date, created_at) VALUES
(1, 'FK-PJ2026001-2026-0001', 1, 1, 'PROGRESS', 12000000.00, 3280000.00, 12100000.00, '土方完成70%进度款', 'PAID', 12000000.00, 12000000.00, '2026-04-15', NOW()),
(2, 'FK-PJ2026001-2026-0002', 1, 2, 'PROGRESS', 56000000.00, 28000000.00, 56000000.00, '主体至10层进度款', 'APPROVED', 56000000.00, 0, NULL, NOW());

INSERT INTO cost_invoice (project_id, contract_id, payment_id, supplier_id, invoice_no, invoice_type, amount, tax_amount, invoice_date, check_status, created_at) VALUES
(1, 1, 1, 3, '044031900111', '增值税专用', 12100000.00, 1100000.00, '2026-04-12', 'PASSED', NOW()),
(1, 2, 2, 1, '044031900112', '增值税专用', 56000000.00, 5090909.09, '2026-08-18', 'PASSED', NOW());

-- ========== 动态成本快照(2026-08) ==========
INSERT INTO cost_dynamic_snapshot (project_id, snap_month, target_total, contracted_total, confirmed_change, estimated_change, non_contract, remaining, dynamic_total, created_at) VALUES
(1, '2026-08', 492800000.00, 312800000.00, 1820000.00, 950000.00, 0, 178230000.00, 315570000.00, NOW());

-- ========== 无效成本 ==========
INSERT INTO cost_invalid (project_id, invalid_type, amount, occurred_date, contract_id, responsible_dept, description, status, created_at) VALUES
(1, '返工损失', 450000.00, '2026-06-20', 2, '工程管理部', '2#楼三层剪力墙胀模返工', 'CONFIRMED', NOW());

-- ========== 成本指标库 ==========
INSERT INTO cost_indicator (account_code, product_category, indicator_type, indicator_name, unit, value, source, created_at) VALUES
('0302', '高层住宅', 'COST', '主体建筑单方', '元/㎡', 3250.00, '星汇湾一期/基准版', NOW()),
('0303', '高层住宅', 'COST', '主体安装单方', '元/㎡', 1180.00, '星汇湾一期/基准版', NOW()),
('0302', '高层住宅', 'CONTENT', '钢筋含量', 'kg/㎡', 48.50, '星汇湾一期', NOW()),
('0302', '高层住宅', 'CONTENT', '混凝土含量', 'm³/㎡', 0.42, '星汇湾一期', NOW());

-- ========== 招采全链演示：机电安装总承包 ==========
INSERT INTO proc_requirement (id, req_code, project_id, title, account_id, description, estimate_amount, expect_date, urgency, status, created_at) VALUES
(1, 'XQ-202608-0001', 1, '星汇湾一期机电安装总承包需求', 10, '给排水/电气/暖通安装总承包', 145000000.00, '2026-09-10', 'URGENT', 'APPROVED', NOW());

INSERT INTO proc_plan (id, plan_code, project_id, plan_month, name, status, created_at) VALUES
(1, 'JH-202609-001', 1, '2026-09', '星汇湾9月采购计划', 'CONFIRMED', NOW());
INSERT INTO proc_plan_item (plan_id, requirement_id, title, estimate_amount, expect_date, created_at) VALUES
(1, 1, '机电安装总承包', 145000000.00, '2026-09-10', NOW());

INSERT INTO proc_project (id, project_code, project_id, title, budget_amount, plan_item_id, fund_source, procure_method, period_days, status, created_at) VALUES
(1, 'ZC-202608-0001', 1, '星汇湾一期机电安装总承包招标', 148000000.00, 3, '自有资金', 'PUBLIC_BID', 420, 'EVALUATING', NOW());

INSERT INTO proc_entry (proc_project_id, supplier_id, supplier_name, precheck_result, inspection_score, inspection_conclusion, is_candidate, status, created_at) VALUES
(1, 1, '钱江建工集团股份有限公司', 'PASSED', 88.00, 'GOOD', 1, 'APPROVED', NOW()),
(1, 2, '之江建设集团有限公司', 'PASSED', 90.00, 'GOOD', 1, 'APPROVED', NOW()),
(1, 6, '甬城市政建设集团股份有限公司', 'PASSED', 82.00, 'PASS', 1, 'APPROVED', NOW()),
(1, 7, '杭州云设计建筑设计有限公司', 'PASSED', 79.00, 'PASS', 0, 'APPROVED', NOW());

INSERT INTO proc_tender (id, tender_code, proc_project_id, doc_version, deposit_amount, publish_time, deadline, status, created_at) VALUES
(1, 'FB-202608-0001', 1, 'V1.2', 2000000.00, '2026-08-20 10:00:00', '2026-08-30 17:00:00', 'OPENED', NOW());

INSERT INTO proc_bid (id, tender_id, proc_project_id, supplier_id, supplier_name, bid_time, price_amount, bid_ip, compliance_result, bid_status, created_at) VALUES
(1, 1, 1, 1, '钱江建工集团股份有限公司', '2026-08-30 16:20:00', 145600000.00, '203.0.113.11', 'PASSED', 'OPENED', NOW()),
(2, 1, 1, 2, '之江建设集团有限公司', '2026-08-30 16:35:00', 142800000.00, '203.0.113.12', 'PASSED', 'OPENED', NOW()),
(3, 1, 1, 6, '甬城市政建设集团股份有限公司', '2026-08-30 16:40:00', 147900000.00, '203.0.113.12', 'PASSED', 'OPENED', NOW()),
(4, 1, 1, 7, '杭州云设计建筑设计有限公司', '2026-08-30 16:50:00', 151200000.00, '198.51.100.5', 'FAILED', 'OPENED', NOW());

INSERT INTO proc_collusion_warn (proc_project_id, rule_code, detail, risk_level, created_at) VALUES
(1, 'SAME_IP', '之江建设与甬城市政回标IP相同(203.0.113.12)，存在围串标嫌疑', 'HIGH', NOW());

INSERT INTO proc_evaluation (id, proc_project_id, method, tech_weight, biz_weight, expert_names, status, conclusion, created_at) VALUES
(1, 1, 'COMPREHENSIVE', 60.00, 40.00, '专家:钱工/孙工/周工/李工', 'REPORTED', '综合评分前三：之江建设、钱江建工、甬城市政', NOW());

INSERT INTO proc_eval_detail (evaluation_id, bid_id, supplier_name, expert_name, tech_score, biz_score, total_score, rank_no, created_at) VALUES
(1, 1, '钱江建工集团股份有限公司', '钱工', 91.00, 97.80, 93.72, 2, NOW()),
(1, 2, '之江建设集团有限公司', '钱工', 92.00, 99.53, 95.01, 1, NOW()),
(1, 3, '甬城市政建设集团股份有限公司', '钱工', 85.00, 96.07, 89.53, 3, NOW());

-- ========== 直接委托 + 战略集采 ==========
INSERT INTO proc_direct (id, project_id, title, supplier_id, supplier_name, amount, direct_reason, reason_desc, status, created_at) VALUES
(1, 1, '桩基检测委托', 9, '浙江鉴衡工程检测有限公司', 1680000.00, '垄断', '本地区具备资质的检测机构仅两家，另一家排期冲突', 'APPROVED', NOW());

INSERT INTO proc_framework (id, framework_code, name, supplier_id, supplier_name, category_id, unit_price_table, valid_from, valid_to, status, created_at) VALUES
(1, 'ZC-2026-007', '集团电梯战略集采协议(2026)', 8, '恒信建材贸易有限公司', NULL, '{"商品混凝土C30":{"unit":"m³","price":485},"钢筋HRB400":{"unit":"t","price":3850}}', '2026-01-01', '2026-12-31', 'ACTIVE', NOW());
INSERT INTO proc_framework_order (order_code, framework_id, project_id, content, quantity, amount, order_date, status, created_at) VALUES
('DD-20260612-001', 1, 1, '商品混凝土C30', 3200.00, 1552000.00, '2026-06-12', 'DELIVERED', NOW()),
('DD-20260902-002', 1, 2, '钢筋HRB400', 260.00, 1001000.00, '2026-09-02', 'ORDERED', NOW());

-- ========== 投资货值 ==========
INSERT INTO inv_calc_version (id, version_code, project_id, stage, sale_value, total_cost, three_fee, tax_amount, net_profit, profit_rate, irr, payback_years, decision_notes, locked, status, created_at) VALUES
(1, 'CS-PJ2026001-LAND', 1, 'LAND', 2680000000.00, 2280000000.00, 121000000.00, 219000000.00, 60000000.00, 2.24, 8.90, 3.10, '2025年11月投决会通过：地价21.5亿，货值26.8亿', 1, 'LOCKED', NOW());

INSERT INTO inv_value (project_id, product_category, area, price_version, price, total_value, supply_date, created_at) VALUES
(1, '高层住宅', 34400.00, 'REGISTERED', 29800.00, 1025120000.00, '2026-09-30', NOW()),
(1, '花园洋房', 9600.00, 'REGISTERED', 36500.00, 350400000.00, '2026-12-31', NOW()),
(1, '沿街商铺', 4000.00, 'REGISTERED', 52000.00, 208000000.00, '2027-03-31', NOW()),
(1, '地下车位', 1000.00, 'FORECAST',  180000.00, 180000000.00, '2027-06-30', NOW());

INSERT INTO inv_value_flow (project_id, flow_month, sold_value, refund_value, supply_value, stock_value, created_at) VALUES
(1, '2026-08', 0, 0, 0, 1763520000.00, NOW());

INSERT INTO inv_dynamic_profit (project_id, snap_month, dynamic_value, dynamic_cost, three_fee, tax_amount, dynamic_profit, profit_rate, baseline_profit, created_at) VALUES
(1, '2026-08', 1763520000.00, 315570000.00, 43000000.00, 144100000.00, 1259850000.00, 71.43, 60000000.00, NOW());

-- ========== 资金 ==========
INSERT INTO fund_plan (plan_month, project_id, version_type, receipt_plan, payment_plan, net_plan, receipt_actual, payment_actual, gap_amount, created_at) VALUES
('2026-08', 1, 'FINAL', 68000000.00, 152300000.00, -84300000.00, 65000000.00, 148800000.00, 0, NOW()),
('2026-09', 1, 'DRAFT', 92000000.00, 86500000.00, 5500000.00, 0, 0, 0, NOW());

INSERT INTO fund_finance (project_id, finance_type, bank_name, amount, used_amount, rate, start_date, due_date, status, created_at) VALUES
(1, '开发贷', '工商银行杭州分行', 1200000000.00, 680000000.00, 4.350, '2026-01-15', '2028-01-14', 'NORMAL', NOW()),
(2, '开发贷', '建设银行南京分行', 700000000.00,  120000000.00, 4.100, '2026-06-20', '2028-06-19', 'NORMAL', NOW());

-- ========== 集成平台调用日志 ==========
INSERT INTO int_call_log (adapter_code, api_code, direction, request_digest, response_digest, success, cost_ms, created_at) VALUES
('ADP_KINGDEE', 'API_CONTRACT_PUSH', 'OUT', '{"contract":"HT-PJ2026001-2026-0001"}', '{"code":"0","voucher":"PZ-202601-0088"}', 1, 342, NOW()),
('ADP_KINGDEE', 'API_RECEIPT_PULL',  'OUT', '{"month":"2026-04"}', '{"code":"0","payments":12}', 1, 508, NOW()),
('ADP_BAIWANG', 'API_INVOICE_CHECK', 'OUT', '{"invoiceNo":"044031900111"}', '{"valid":true}', 1, 275, NOW()),
('ADP_BAIWANG', 'API_INVOICE_CHECK', 'OUT', '{"invoiceNo":"044031900112"}', '{"valid":true}', 1, 261, NOW()),
('ADP_NETSIGN', NULL, 'OUT', '{"city":"杭州"}', '{"code":"timeout"}', 0, 30000, NOW());
