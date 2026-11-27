-- =====================================================
-- V48 楼栋房源管理：主数据(2) 下新增「楼栋房源」菜单
--   整栋搭建(层数/单元/房号) · 可视化楼栋看板 · 房间面积/表价/底价 · 删除/合并
-- =====================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(210, 2, '楼栋房源', 'M', '/mdm/building', 'mdm/building/index', 'OfficeBuilding', 10);

-- 角色授权：集团管理员(1) / 成本经理(2) / 招采经理(3) / 销售经理(6)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 210), (2, 210), (3, 210), (6, 210);
