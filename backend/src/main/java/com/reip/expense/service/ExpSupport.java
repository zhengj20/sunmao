package com.reip.expense.service;

import com.reip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 费用模块公共支撑：金额兜底 / 发票查重 hash
 */
@Component
@RequiredArgsConstructor
public class ExpSupport {

    public static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static void requireAmount(BigDecimal v, String label) {
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("请填写" + label);
    }

    /** duplicate_hash = SHA-256(税号|发票号)，作为全平台查重键 */
    public static String invoiceHash(String taxNo, String invNo) {
        String base = (taxNo == null ? "" : taxNo.trim()) + "|" + (invNo == null ? "" : invNo.trim());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(base.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return base; // 理论不可达
        }
    }
}
