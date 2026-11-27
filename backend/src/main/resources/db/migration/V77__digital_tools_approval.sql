-- =====================================================
-- V77 数字员工：工具模块 + 审批中心菜单（对照 shuzi 参考项目）
-- =====================================================

-- ---------- 工具（HTTP 工具 + 内置工具，技能可绑定，执行器调用） ----------
CREATE TABLE dig_tool (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tool_code VARCHAR(64) NOT NULL COMMENT '工具编码（内置 calc/biz.snapshot；自定义 TOOL-xxx）',
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  type VARCHAR(16) NOT NULL DEFAULT 'HTTP' COMMENT 'BUILTIN=内置 / HTTP=自定义HTTP',
  url VARCHAR(500) COMMENT 'HTTP 地址',
  method VARCHAR(8) DEFAULT 'GET' COMMENT 'GET/POST',
  headers TEXT COMMENT '请求头 JSON',
  body TEXT COMMENT '请求体（POST）',
  status VARCHAR(16) DEFAULT 'ACTIVE',
  tenant_id BIGINT,
  created_by BIGINT,
  created_at DATETIME,
  updated_by BIGINT,
  updated_at DATETIME,
  version INT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_tool_code (tool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：内置工具
INSERT INTO dig_tool (tool_code, name, description, type, status, created_at, updated_at) VALUES
('calc', '计算器', '安全求值算术表达式（+ - * /、括号、^ 幂），如 (100+200)*0.5', 'BUILTIN', 'ACTIVE', NOW(), NOW()),
('biz.snapshot', '业务数据快照', '实时查询核心业务数据：签约额/回款/回款率/动态利润/成本偏差/去化率等', 'BUILTIN', 'ACTIVE', NOW(), NOW());

-- ---------- 技能绑定工具 + 工具参数 ----------
ALTER TABLE dig_skill ADD COLUMN tool_ids VARCHAR(512) DEFAULT NULL COMMENT '绑定的工具ID（逗号分隔）' AFTER kb_search;
ALTER TABLE dig_skill ADD COLUMN tool_args TEXT DEFAULT NULL COMMENT '工具参数 JSON，如 {"calc":"(1+2)*3"}' AFTER tool_ids;

-- ---------- 菜单：审批中心 / 工具 ----------
INSERT INTO sys_menu (parent_id, name, type, path, component, perm_code, icon, sort, visible, created_at, updated_at) VALUES
(500, '审批中心', 'M', '/digital/approval', 'digital/approval/index', NULL, 'Stamp', 3, 1, NOW(), NOW()),
(500, '工具', 'M', '/digital/tool', 'digital/tool/index', NULL, 'SetUp', 5, 1, NOW(), NOW());

-- 角色授权：与员工团队菜单（507）相同的角色自动获得新菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.id
FROM sys_role_menu rm
JOIN sys_menu m ON m.path IN ('/digital/approval', '/digital/tool')
WHERE rm.menu_id = 507
GROUP BY rm.role_id, m.id;
