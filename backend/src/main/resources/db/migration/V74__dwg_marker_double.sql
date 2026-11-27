-- =====================================================
-- V74 在线审图：标注点坐标改为 DOUBLE
--   原 DECIMAL(6,4) 上限 99.9999，仅够「图片 0-100 百分比」。
--   CAD(DXF) 记号改用「图纸场景坐标」，数值可能远大于 100（如 12345.67），
--   溢出 DECIMAL(6,4) 会导致入库 500。改为 DOUBLE 以同时兼容：
--     · 图片图纸：marker 存 0-100 百分比
--     · CAD 图纸：marker 存场景坐标（任意量级）
--   实体字段本即为 Double，无需改 Java。
-- =====================================================

ALTER TABLE dwg_issue
  MODIFY COLUMN marker_x DOUBLE NULL COMMENT '标注点X(图片=0-100百分比; CAD=场景坐标)',
  MODIFY COLUMN marker_y DOUBLE NULL COMMENT '标注点Y(图片=0-100百分比; CAD=场景坐标)';
