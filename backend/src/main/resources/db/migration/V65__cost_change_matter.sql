-- 变更事项申请：签证/设计变更的上游"申请/背景"。一个事项可关联多个签证与变更(1:N)。
-- 关系：cost_change_matter(事项) 1 — N cost_change(签证GCQZ/设计变更SJBG)，通过 cost_change.matter_id 关联。

-- 1) 事项申请表
CREATE TABLE cost_change_matter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  matter_code VARCHAR(64) NOT NULL COMMENT '事项编号(SM规则)',
  project_id BIGINT NOT NULL,
  contract_id BIGINT COMMENT '关联合同(可空,事项可为项目级)',
  account_id BIGINT COMMENT '无合同挂科目(可空)',
  matter_type VARCHAR(16) COMMENT 'SJBG设计变更类/GCQZ工程签证类/ZH综合',
  title VARCHAR(255) NOT NULL COMMENT '事项名称',
  reason_category VARCHAR(64) COMMENT '原因归类',
  description VARCHAR(1000) COMMENT '事项背景/原因(为什么发生)',
  estimate_total DECIMAL(16,2) COMMENT '预估金额合计(申请口径)',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/APPROVED已批准/CLOSED已关闭',
  applied_by BIGINT COMMENT '申请人',
  apply_date DATE COMMENT '申请日期',
  remark VARCHAR(500),
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_matter_code (matter_code),
  KEY idx_matter_project (project_id), KEY idx_matter_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更事项申请(1事项→N签证/设计变更)';

-- 2) 变更表加事项关联
ALTER TABLE cost_change ADD COLUMN matter_id BIGINT COMMENT '所属事项申请' AFTER contract_id;
ALTER TABLE cost_change ADD KEY idx_change_matter (matter_id);

-- 3) 事项编码规则 SM
INSERT INTO mdm_code_rule (rule_code, name, pattern, example)
VALUES ('SM', '变更事项', 'SM-{projectCode}-{YYYY}-{seq:4}', 'SM-PJ2026001-2026-0001')
ON DUPLICATE KEY UPDATE name = VALUES(name), pattern = VALUES(pattern);
INSERT INTO mdm_code_counter (rule_code, dim_key, current_no)
SELECT 'SM', DATE_FORMAT(CURDATE(), '%Y'), 2
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, 2);

-- 4) 演示事项（事项1 综合：含签证1+设计变更3，体现 1事项→N变更；事项2 设计变更类）
INSERT INTO cost_change_matter (id, matter_code, project_id, contract_id, matter_type, title, reason_category, description, estimate_total, status, applied_by, apply_date, created_at) VALUES
(1, 'SM-PJ2026001-2026-0001', 1, 2, 'ZH',   '1#楼地下室底板加厚专项', '地质条件变化', '局部遇暗塘，底板需加厚并优化配筋，涉及工程签证与设计变更', 2500000.00, 'APPROVED', 2, '2026-03-10', NOW()),
(2, 'SM-PJ2026001-2026-0002', 1, 2, 'SJBG', '2#楼标准层户型优化',     '产品优化',     '边户型阳台调整，涉及外立面线条与结构变更',           950000.00,  'DRAFT',    2, '2026-04-02', NOW());

-- 5) 演示变更：新增设计变更3，与既有签证1 同挂事项1（1事项→2变更，签证+设计变更并存）
INSERT INTO cost_change (id, change_code, project_id, contract_id, matter_id, change_type, title, reason_category, description, estimate_amount, confirmed_amount, deduct_amount, status, require_contract, created_at) VALUES
(3, 'BG-PJ2026001-2026-0003', 1, 2, 1, 'SJBG', '1#楼地下室底板钢筋配筋优化', '产品优化', '底板加厚后配筋重新计算优化', 650000.00, NULL, 0, 'ESTIMATING', 1, NOW());

-- 6) 既有变更关联到事项
UPDATE cost_change SET matter_id = 1 WHERE id = 1;
UPDATE cost_change SET matter_id = 2 WHERE id = 2;
