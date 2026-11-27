-- =====================================================
-- V73 在线审图：审查问题支持「图面标注点」坐标 + 在线审图菜单
--   1) dwg_issue 增加 marker_x / marker_y（0~100 相对图纸宽高的百分比，可空；
--      人工在图面上点击标注的问题会带坐标，AI 预审问题不带坐标）。
--   2) 菜单：工程审图(600) 下新增 在线审图(604)。
-- =====================================================

ALTER TABLE dwg_issue
  ADD COLUMN marker_x DECIMAL(6,4) NULL COMMENT '标注点X(0-100,占图纸宽度百分比)' AFTER seq,
  ADD COLUMN marker_y DECIMAL(6,4) NULL COMMENT '标注点Y(0-100,占图纸高度百分比)' AFTER marker_x;

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(604, 600, '在线审图', 'M', '/dwg/online', 'dwg/online/index', 'EditPen', 4);

-- 角色授权：集团管理员(1) / 成本经理(2) / 项目运营(7)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 604), (2, 604), (7, 604);
