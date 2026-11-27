package com.reip.framework.coding;

import com.reip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编码规则引擎（MDM 编码规则的运行时）
 * 模式占位符：{YYYY} {YY} {MM} {DD} {seq:n} {ctx任意键，如 projectCode}
 * 重置维度 reset_scope：GLOBAL / YEAR / MONTH / DAY / PROJECT
 * 并发安全：同事务内对计数器行 FOR UPDATE 串行化
 */
@Service
@RequiredArgsConstructor
public class CodeRuleService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)(?::(\\d+))?}");

    private final CodeRuleMapper codeRuleMapper;

    @Transactional
    public String next(String ruleCode, Map<String, String> ctx) {
        String pattern = codeRuleMapper.findPattern(ruleCode);
        if (pattern == null) throw new BizException("编码规则不存在：" + ruleCode);
        String resetScope = resetScopeOf(pattern);
        String dimKey = dimKey(resetScope, ctx);
        codeRuleMapper.ensureCounter(ruleCode, dimKey);
        Long no = codeRuleMapper.lockCurrentNo(ruleCode, dimKey);
        if (no == null) no = 0L;
        long nextNo = no + 1;
        codeRuleMapper.updateNo(ruleCode, dimKey, nextNo);

        LocalDate now = LocalDate.now();
        Matcher m = PLACEHOLDER.matcher(pattern);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String width = m.group(2);
            String val;
            switch (key) {
                case "YYYY" -> val = String.valueOf(now.getYear());
                case "YY" -> val = String.valueOf(now.getYear() % 100);
                case "MM" -> val = "%02d".formatted(now.getMonthValue());
                case "DD" -> val = "%02d".formatted(now.getDayOfMonth());
                case "seq" -> {
                    int w = width == null ? 4 : Integer.parseInt(width);
                    val = String.format("%0" + w + "d", nextNo);
                }
                default -> {
                    val = ctx == null ? null : ctx.get(key);
                    if (val == null) val = "";
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 规则未显式配置维度时默认按年重置 */
    private String resetScopeOf(String pattern) {
        if (pattern.contains("|GLOBAL|")) return "GLOBAL";
        if (pattern.contains("|MONTH|")) return "MONTH";
        if (pattern.contains("|DAY|")) return "DAY";
        if (pattern.contains("|PROJECT|")) return "PROJECT";
        return "YEAR";
    }

    private String dimKey(String scope, Map<String, String> ctx) {
        LocalDate now = LocalDate.now();
        return switch (scope) {
            case "GLOBAL" -> "G";
            case "MONTH" -> now.getYear() + "-" + "%02d".formatted(now.getMonthValue());
            case "DAY" -> now.toString();
            case "PROJECT" -> (ctx == null ? "" : ctx.getOrDefault("projectCode", "")) + ":" + now.getYear();
            default -> String.valueOf(now.getYear());
        };
    }
}
