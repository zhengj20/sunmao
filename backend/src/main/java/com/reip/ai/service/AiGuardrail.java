package com.reip.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * AI 护栏：敏感数据脱敏、输出留痕摘要、置信度阈值、"仅展示不代批"提示。
 */
@Component
public class AiGuardrail {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final int MAX_DIGEST = 1000;

    /** 脱敏：手机号/证件号（R2 敏感数据裁剪） */
    public String sanitize(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = PHONE.matcher(text).replaceAll(m -> m.group().substring(0, 3) + "****" + m.group().substring(7));
        out = ID_CARD.matcher(out).replaceAll(m -> m.group().substring(0, 4) + "**********" + m.group().substring(14));
        return out;
    }

    /** 生成留痕摘要：脱敏 + 截断（R3） */
    public String digest(String text) {
        String s = sanitize(text);
        if (s == null) return null;
        if (s.length() <= MAX_DIGEST) return s;
        return s.substring(0, MAX_DIGEST) + "…";
    }

    /** 置信度阈值：LLM 输出过短视为低置信 → 调用方应回退规则（R4） */
    public boolean isLowConfidence(String content) {
        return content == null || content.isBlank() || content.length() < 10;
    }

    /** "仅展示不代批"统一提示 */
    public String advisory() {
        return "以上为 AI 预审建议，仅供审批人参考，最终审批由人工 + 流程引擎决定。";
    }
}
