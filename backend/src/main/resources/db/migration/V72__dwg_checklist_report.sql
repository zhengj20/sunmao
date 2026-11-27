-- =====================================================
-- V72 工程审图完善：审查要点库(可配置) + 审查报告菜单
--   1) dwg_checklist：把 AI 预审的「审查要点」从代码硬编码迁移到可维护的数据库库；
--      专业=ALL 表示通用要点；stage 为空=所有阶段，否则仅该阶段生效；enabled=0 停用。
--   2) 菜单：工程审图(600) 下新增 审查要点库(602) / 审查报告(603)。
-- =====================================================

CREATE TABLE dwg_checklist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  discipline VARCHAR(32) NOT NULL COMMENT '专业 ARCH/STRUCT/ELEC/PLUMB/HVAC/INTERIOR/LANDSCAPE/ALL通用',
  stage VARCHAR(32) COMMENT '阶段为空=所有阶段；否则仅该阶段生效',
  level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重度 HIGH/MEDIUM/LOW',
  category VARCHAR(32) COMMENT '分类 图面表达/规范符合性/尺寸标注/专业冲突/材料设备/安全消防/节能环保/其他',
  description VARCHAR(1000) NOT NULL COMMENT '审查要点描述',
  suggestion VARCHAR(500) COMMENT '修改建议',
  enabled TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  sort INT DEFAULT 0 COMMENT '排序',
  tenant_id BIGINT DEFAULT 1,
  created_by BIGINT, created_at DATETIME, updated_by BIGINT, updated_at DATETIME,
  version INT DEFAULT 0, deleted TINYINT DEFAULT 0,
  KEY idx_cl_discipline (discipline),
  KEY idx_cl_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸审查要点库(AI预审规则源)';

-- 通用要点（专业=ALL，所有阶段）
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('ALL', NULL, 'MEDIUM', '规范符合性', '核对图纸目录、总说明、设计依据与所引规范是否为现行有效版本', '补充或更新规范版本及设计依据清单', 1, 1),
('ALL', NULL, 'MEDIUM', '图面表达', '检查图框、图签、版本号、比例、图例是否完整规范，标注是否清晰可读', '补齐图签要素并核对版本号一致性', 1, 2),
('ALL', 'CONSTRUCTION', 'MEDIUM', '规范符合性', '施工图阶段：核对设计说明深度、计算书与图面是否一致', '核对计算书与图面数据一致性', 1, 900);

-- 建筑 ARCH
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('ARCH', NULL, 'HIGH', '安全消防', '核对防火分区划分、安全出口数量与疏散距离是否满足规范要求', '复核疏散距离与防火分区，必要时调整平面布局', 1, 11),
('ARCH', NULL, 'HIGH', '安全消防', '检查无障碍设计（坡道、扶手、无障碍卫生间）是否齐备', '补充无障碍设计做法', 1, 12),
('ARCH', NULL, 'MEDIUM', '规范符合性', '核对层高/净高、门窗开启方向、防坠落与栏杆高度', '核对竖向尺寸与栏杆做法', 1, 13),
('ARCH', NULL, 'MEDIUM', '材料设备', '核对屋面/外墙/卫生间防水做法与材料选型', '明确防水材料与节点做法', 1, 14),
('ARCH', NULL, 'LOW', '专业冲突', '核对建筑平面与结构、机电预留洞及设备基础是否存在冲突(错漏碰缺)', '与结构/机电核对预留预留洞口', 1, 15);

-- 结构 STRUCT
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('STRUCT', NULL, 'HIGH', '规范符合性', '核对基础形式与地勘报告持力层、地下水位是否匹配', '复核地勘参数与基础选型', 1, 21),
('STRUCT', NULL, 'HIGH', '规范符合性', '检查最小配筋率、钢筋锚固/搭接长度与节点构造', '复核配筋与节点详图', 1, 22),
('STRUCT', NULL, 'MEDIUM', '规范符合性', '核对混凝土保护层、裂缝控制与抗震构造措施', '核对保护层与抗震等级要求', 1, 23),
('STRUCT', NULL, 'MEDIUM', '尺寸标注', '核对梁柱截面、结构标高与轴线尺寸标注是否完整准确', '补齐结构标高与尺寸标注', 1, 24),
('STRUCT', NULL, 'LOW', '专业冲突', '核对结构预留洞口与机电管线路由是否冲突', '与机电核对洞口位置', 1, 25);

-- 电气 ELEC
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('ELEC', NULL, 'HIGH', '安全消防', '核对负荷等级、双回路/应急电源配置与防雷接地做法', '复核负荷等级与接地系统', 1, 31),
('ELEC', NULL, 'MEDIUM', '规范符合性', '核对线路选型、桥架/管径规格与防火封堵', '核对桥架规格与防火封堵做法', 1, 32),
('ELEC', NULL, 'MEDIUM', '安全消防', '检查应急照明、疏散指示与火灾报警联动', '补齐应急照明与报警联动说明', 1, 33),
('ELEC', NULL, 'LOW', '专业冲突', '核对桥架路径与结构、风管、水管是否存在冲突', '与结构/暖通核对路由', 1, 34);

-- 给排水 PLUMB
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('PLUMB', NULL, 'HIGH', '安全消防', '核对消火栓/喷淋系统布置、消防水泵房与高位水箱', '复核消防水量与泵房布置', 1, 41),
('PLUMB', NULL, 'MEDIUM', '规范符合性', '核对管径与流量、坡度与管材连接方式', '复核管径计算与连接做法', 1, 42),
('PLUMB', NULL, 'MEDIUM', '尺寸标注', '核对预留洞口尺寸、管井布置与标高', '补齐管井与预留洞标注', 1, 43),
('PLUMB', NULL, 'LOW', '专业冲突', '核对给排水管线与电气、暖通管线路由是否冲突', '与电气/暖通核对管线综合', 1, 44);

-- 暖通 HVAC
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('HVAC', NULL, 'MEDIUM', '规范符合性', '核对冷热源选型、系统形式、风管尺寸与保温做法', '复核系统选型与保温做法', 1, 51),
('HVAC', NULL, 'MEDIUM', '节能环保', '核对新风量、排烟量计算与设备能效指标', '复核能耗指标与设备能效', 1, 52),
('HVAC', NULL, 'MEDIUM', '安全消防', '核对防排烟系统设计与防火阀设置', '复核防排烟与防火阀布置', 1, 53),
('HVAC', NULL, 'LOW', '专业冲突', '核对风管路径与结构梁、电气桥架是否冲突', '与结构/电气核对路由', 1, 54);

-- 精装修 INTERIOR
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('INTERIOR', NULL, 'MEDIUM', '图面表达', '核对装饰做法、材料样板与收口节点图是否齐全', '补齐收口节点详图', 1, 61),
('INTERIOR', NULL, 'MEDIUM', '材料设备', '核对主材品牌、规格、环保等级与防火等级', '明确材料品牌与防火等级', 1, 62),
('INTERIOR', NULL, 'LOW', '专业冲突', '核对精装与机电末端点位、吊顶标高是否冲突', '与机电核对末端点位', 1, 63);

-- 景观 LANDSCAPE
INSERT INTO dwg_checklist (discipline, stage, level, category, description, suggestion, enabled, sort) VALUES
('LANDSCAPE', NULL, 'MEDIUM', '规范符合性', '核对竖向设计、排水组织与海绵城市指标', '复核竖向与海绵指标', 1, 71),
('LANDSCAPE', NULL, 'MEDIUM', '材料设备', '核对铺装、苗木、小品做法与选型', '明确铺装与苗木选型', 1, 72),
('LANDSCAPE', NULL, 'LOW', '节能环保', '核对雨水收集、节水灌溉与照明节能做法', '补充节水与节能做法', 1, 73);

-- =====================================================
-- 菜单：审查要点库(602) + 审查报告(603)，挂在工程审图(600) 下
-- =====================================================
INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, sort) VALUES
(602, 600, '审查要点库', 'M', '/dwg/checklist', 'dwg/checklist/index', '', 2),
(603, 600, '审查报告',   'M', '/dwg/report',    'dwg/report/index',   '', 3);

-- 角色授权：集团管理员(1) / 成本经理(2) / 项目运营(7)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 602), (1, 603),
(2, 602), (2, 603),
(7, 602), (7, 603);
