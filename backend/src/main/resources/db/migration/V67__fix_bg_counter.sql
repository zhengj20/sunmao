-- 修复：V65 直接插入了 change_code=BG-...-0003（演示变更3），但未推进 BG 计数器，
-- 导致新建变更时 codeRule 仍生成 BG-...-0003 撞唯一键。此处将 BG 计数器对齐到现有变更数量。
UPDATE mdm_code_counter
SET current_no = GREATEST(current_no, (SELECT COUNT(*) FROM cost_change WHERE deleted = 0))
WHERE rule_code = 'BG' AND dim_key = DATE_FORMAT(CURDATE(), '%Y');
