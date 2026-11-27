-- =====================================================
-- V42 知识库集成：数字员工/AI助手/审批/报告 均可检索知识库
-- =====================================================

-- 1. 技能增加「是否检索知识库」开关（默认开启）
ALTER TABLE dig_skill ADD COLUMN kb_search TINYINT DEFAULT 1 COMMENT '是否检索知识库 1=是 0=否' AFTER install_count;

-- 2. 菜单改名：AI问答 → AI助手
UPDATE sys_menu SET name = 'AI助手' WHERE id = 502;
