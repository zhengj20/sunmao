-- =====================================================
-- V16 主数据数据质量巡检
-- =====================================================

CREATE TABLE mdm_quality_issue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type VARCHAR(32) NOT NULL COMMENT 'SUPPLIER供应商/CUSTOMER客户/PROJECT项目',
  entity_id BIGINT NOT NULL COMMENT '主数据实体ID',
  issue_type VARCHAR(32) NOT NULL COMMENT 'MISSING_VALUE缺失/DUPLICATE重复',
  detail VARCHAR(512) COMMENT '问题描述',
  severity VARCHAR(16) DEFAULT 'WARN' COMMENT '严重度(WARN默认)',
  status VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN待处理/FIXED已修复/IGNORED已忽略',
  scanned_at DATETIME COMMENT '扫描时间',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_qi_status_type (status, entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主数据数据质量巡检问题';

-- 菜单：主数据(2) 下新增「数据质量」
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(29, 2, '数据质量', 'M', '/mdm/quality', 'mdm/quality/index', '', 9);

-- 角色授权：授予含主数据权限的角色（1集团管理员 / 2成本经理 / 3招采经理）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 29), (2, 29), (3, 29);
