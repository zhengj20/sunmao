-- 合同付款支持：付款单可不关联报销单（纯合同付款），reimb_id 允许 NULL
ALTER TABLE exp_payment MODIFY reimb_id BIGINT NULL COMMENT '关联报销单(纯合同付款时为空)';
