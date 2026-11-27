package com.reip.budget.service;

import com.reip.common.exception.BizException;

import java.math.BigDecimal;

/**
 * 全面预算模块公共支撑：金额兜底 / 必填校验
 */
public final class BudSupport {

    private BudSupport() {
    }

    public static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static void requireAmount(BigDecimal v, String label) {
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写" + label);
    }
}
