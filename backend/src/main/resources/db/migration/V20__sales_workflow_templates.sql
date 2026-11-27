-- =====================================================
-- V20 销售审批流模板（补充 V19 缺少的"认购审批 / 签约审批"两条正向流）
--   V19 已定义：SALE_REFUND(退房) / SALE_SUBSCRIPTION_TERMINATE(退定) / SALE_SPECIAL_PRICE(特价)
--   本迁移补齐销售主链的正向审批：
--     SALE_SUBSCRIPTION  认购审批：销售经理 → 结束
--     SALE_CONTRACT      签约审批：销售经理 →(金额≥50万)集团会签 → 结束
-- 审批角色：SALES_MGR(销售经理,V19 已建) / ADMIN(集团管理员,既有)
-- 唯一约束 uk_tpl_biz_active(biz_type, status)：同 biz_type 仅一条 ACTIVE
-- =====================================================

INSERT INTO wfl_template (template_code, name, biz_type, definition, remark) VALUES
('TPL_SALE_SUBSCRIPTION', '认购审批流', 'SALE_SUBSCRIPTION',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"销售经理","assigneeType":"ROLE","assignees":["SALES_MGR"],"countersign":"ANY"},{"id":"n2","type":"END","name":"结束"}],"start":"n1"}',
 '认购生效：审批通过后房源转 SUBSCRIBED'),
('TPL_SALE_CONTRACT', '签约审批流', 'SALE_CONTRACT',
 '{"nodes":[{"id":"n1","type":"APPROVE","name":"销售经理","assigneeType":"ROLE","assignees":["SALES_MGR"],"countersign":"ANY"},{"id":"n2","type":"CONDITION","name":"金额路由","branches":[{"when":{"field":"amount","op":">=","value":500000},"next":"n3"},{"next":"n4"}]},{"id":"n3","type":"APPROVE","name":"集团审批","assigneeType":"ROLE","assignees":["ADMIN"],"countersign":"ALL"},{"id":"n4","type":"END","name":"结束"}],"start":"n1"}',
 '签约生效：审批通过后房源转 CONTRACTED 并生成应收；≥50万上集团会签');
