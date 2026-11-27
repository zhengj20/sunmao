package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmCodeRule;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编码规则管理：CRUD 与预览（预览不消耗计数器）
 */
@Service
@RequiredArgsConstructor
public class MdmCodeRuleService {

    /** 与 CodeRuleService 一致的占位符：{YYYY} {YY} {MM} {DD} {seq:n} {ctx任意键} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)(?::(\\d+))?}");

    private final MdmMappers.CodeRuleMapper codeRuleMapper;

    public PageResult<MdmCodeRule> page(long current, long size, String kw) {
        LambdaQueryWrapper<MdmCodeRule> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(MdmCodeRule::getRuleCode, kw)
                .or().like(MdmCodeRule::getName, kw));
        qw.orderByAsc(MdmCodeRule::getRuleCode);
        Page<MdmCodeRule> p = codeRuleMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Long create(MdmCodeRule rule) {
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()) throw new BizException("规则编码不能为空");
        if (rule.getName() == null || rule.getName().isBlank()) throw new BizException("规则名称不能为空");
        if (rule.getPattern() == null || rule.getPattern().isBlank()) throw new BizException("编码模式不能为空");
        Long cnt = codeRuleMapper.selectCount(new LambdaQueryWrapper<MdmCodeRule>()
                .eq(MdmCodeRule::getRuleCode, rule.getRuleCode()));
        if (cnt > 0) throw new BizException("规则编码已存在");
        rule.setId(null);
        codeRuleMapper.insert(rule);
        return rule.getId();
    }

    @Transactional
    public void update(MdmCodeRule rule) {
        if (rule.getId() == null) throw new BizException("缺少规则ID");
        rule.setRuleCode(null); // 规则编码被业务引用，不可修改
        int updated = codeRuleMapper.updateById(rule);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        codeRuleMapper.deleteById(id);
    }

    /**
     * 预览：解析 pattern 展示示例编码，seq 用当前计数器值+1 演示，不消耗计数器
     */
    public Map<String, Object> preview(String ruleCode) {
        MdmCodeRule rule = codeRuleMapper.selectOne(new LambdaQueryWrapper<MdmCodeRule>()
                .eq(MdmCodeRule::getRuleCode, ruleCode));
        if (rule == null) throw new BizException("编码规则不存在：" + ruleCode);
        String pattern = rule.getPattern();
        String dimKey = dimKey(resetScopeOf(pattern), null);
        Long currentNo = codeRuleMapper.findCurrentNo(ruleCode, dimKey);
        long nextNo = (currentNo == null ? 0 : currentNo) + 1;

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
                default -> val = ""; // 上下文占位符（如 {projectCode}）预览时无上下文
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleCode", rule.getRuleCode());
        result.put("name", rule.getName());
        result.put("pattern", pattern);
        result.put("example", rule.getExample());
        result.put("preview", sb.toString());
        result.put("nextSeq", nextNo);
        return result;
    }

    /** 与 CodeRuleService 一致的重置维度解析 */
    private String resetScopeOf(String pattern) {
        if (pattern.contains("|GLOBAL|")) return "GLOBAL";
        if (pattern.contains("|MONTH|")) return "MONTH";
        if (pattern.contains("|DAY|")) return "DAY";
        if (pattern.contains("|PROJECT|")) return "PROJECT";
        return "YEAR";
    }

    /** 与 CodeRuleService 一致的维度键计算 */
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
