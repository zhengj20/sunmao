-- =====================================================
-- V15 门户公告落库：sys_notice
-- =====================================================

CREATE TABLE sys_notice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(128) NOT NULL COMMENT '公告标题',
  content TEXT COMMENT '公告正文',
  notice_type VARCHAR(32) DEFAULT 'NOTICE' COMMENT '公告类型 NOTICE通知/ANNOUNCEMENT公告/MAINTENANCE维护',
  pinned TINYINT DEFAULT 0 COMMENT '0否 1是(置顶)',
  publish_at DATETIME COMMENT '发布时间',
  publisher_id BIGINT COMMENT '发布人ID',
  publisher_name VARCHAR(64) COMMENT '发布人姓名',
  status VARCHAR(16) DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED已发布/DRAFT草稿/ARCHIVED已归档',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_notice_status_pinned (status, pinned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告';
