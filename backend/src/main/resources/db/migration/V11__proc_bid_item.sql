-- =====================================================
-- V11 招采：评标技术/商务标分离明细(proc_bid_item) + 主流程表字段补充
-- =====================================================

-- 1) 回标明细（技术/商务分离打分项，父 proc_bid.tech_score 按明细加权汇总）
CREATE TABLE proc_bid_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bid_id BIGINT NOT NULL COMMENT '所属回标',
  item_code VARCHAR(64) NOT NULL COMMENT '明细编码(回标内唯一)',
  item_name VARCHAR(255) COMMENT '明细名称',
  tech_score DECIMAL(10,2) COMMENT '技术得分',
  biz_score DECIMAL(10,2) COMMENT '商务得分',
  weight DECIMAL(5,4) DEFAULT 1 COMMENT '权重(默认1)',
  is_invalid TINYINT DEFAULT 0 COMMENT '是否无效 1是 0否(不计入加权)',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_bid_item (bid_id, item_code),
  KEY idx_bpi_bid (bid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回标明细(技术/商务分离)';

-- 2) 字段补充：考察/发标/定标附件与未中标通知
ALTER TABLE proc_entry
  ADD COLUMN inspection_attachment VARCHAR(512) COMMENT '考察附件';

ALTER TABLE proc_tender
  ADD COLUMN doc_attachment VARCHAR(512) COMMENT '标书文件附件',
  ADD COLUMN clarify_attachment VARCHAR(512) COMMENT '答疑纪要附件';

ALTER TABLE proc_award
  ADD COLUMN loser_notify_status VARCHAR(16) DEFAULT 'PENDING' COMMENT '未中标通知 PENDING/SENT';
