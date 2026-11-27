package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.DigTool;
import com.reip.digital.mapper.DigitalMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 工具服务：内置工具（计算器/业务快照）+ 自定义 HTTP 工具。
 * 技能绑定工具后，执行器在「工具调用」步骤统一调用并把结果注入提示词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalToolService {

    private final DigitalMappers.ToolMapper mapper;
    private final DigitalDataFetcher fetcher;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    // ==================== CRUD ====================

    public List<DigTool> list() {
        return mapper.selectList(new LambdaQueryWrapper<DigTool>()
                .eq(DigTool::getStatus, "ACTIVE").orderByAsc(DigTool::getId));
    }

    public DigTool byId(Long id) {
        return mapper.selectById(id);
    }

    /** 保存（新增/更新；内置工具只读） */
    public DigTool save(DigTool t) {
        if (t.getName() == null || t.getName().isBlank()) throw new BizException("工具名称不能为空");
        DigTool existing = t.getId() == null ? null : mapper.selectById(t.getId());
        if (existing != null && "BUILTIN".equals(existing.getType())) {
            throw new BizException("内置工具不可修改");
        }
        if (t.getId() == null) {
            t.setToolCode("TOOL-" + System.currentTimeMillis());
            if (t.getType() == null) t.setType("HTTP");
            if (t.getMethod() == null) t.setMethod("GET");
            if (t.getStatus() == null) t.setStatus("ACTIVE");
            mapper.insert(t);
        } else {
            mapper.updateById(t);
        }
        return t;
    }

    public void delete(Long id) {
        DigTool t = mapper.selectById(id);
        if (t == null) return;
        if ("BUILTIN".equals(t.getType())) throw new BizException("内置工具不可删除");
        mapper.deleteById(id);
    }

    // ==================== 调用 ====================

    /**
     * 调用工具，返回文本结果（失败抛 BizException）。
     * @param argsJson 技能级工具参数 JSON，如 {"calc":"(1+2)*3"}；可为空
     */
    public String invoke(Long toolId, String argsJson) {
        DigTool t = mapper.selectById(toolId);
        if (t == null || !"ACTIVE".equals(t.getStatus())) throw new BizException("工具不存在或已停用");
        Object arg = extractArg(argsJson, t.getToolCode());
        if ("BUILTIN".equals(t.getType())) {
            if ("calc".equals(t.getToolCode())) return invokeCalc(arg);
            if ("biz.snapshot".equals(t.getToolCode())) return invokeBizSnapshot();
            throw new BizException("未知内置工具：" + t.getToolCode());
        }
        return invokeHttp(t);
    }

    private Object extractArg(String argsJson, String toolCode) {
        if (argsJson == null || argsJson.isBlank()) return null;
        try {
            Map<String, Object> m = objectMapper.readValue(argsJson, Map.class);
            return m.get(toolCode);
        } catch (Exception e) {
            return null;
        }
    }

    /** 内置：计算器（安全求值，无 eval） */
    private String invokeCalc(Object arg) {
        if (arg == null || String.valueOf(arg).isBlank()) {
            throw new BizException("计算器缺少表达式参数（技能 toolArgs 需配置 {\"calc\":\"表达式\"}）");
        }
        return String.valueOf(SafeExpr.eval(String.valueOf(arg).trim()));
    }

    /** 内置：业务数据快照（复用取数器的真实业务数据） */
    private String invokeBizSnapshot() {
        StringBuilder sb = new StringBuilder();
        for (String ds : new String[]{"COST", "FIN", "SAL", "PROC"}) {
            Map<String, Object> d = fetcher.fetch(ds);
            if (d.isEmpty()) continue;
            sb.append("[").append(ds).append("] ");
            boolean first = true;
            for (Map.Entry<String, Object> e : d.entrySet()) {
                if ("note".equals(e.getKey())) continue;
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append('\n');
        }
        if (sb.length() == 0) return "（业务数据快照为空）";
        return sb.toString();
    }

    /** 自定义：HTTP 调用 */
    private String invokeHttp(DigTool t) {
        if (t.getUrl() == null || t.getUrl().isBlank()) throw new BizException("工具未配置 URL");
        try {
            String method = (t.getMethod() == null ? "GET" : t.getMethod().toUpperCase());
            HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(t.getUrl().trim()))
                    .timeout(Duration.ofSeconds(20));
            if ("POST".equals(method)) {
                String body = t.getBody() == null ? "" : t.getBody();
                b.POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                b.GET();
            }
            if (t.getHeaders() != null && !t.getHeaders().isBlank()) {
                Map<String, Object> h = objectMapper.readValue(t.getHeaders(), Map.class);
                h.forEach((k, v) -> b.header(k, String.valueOf(v)));
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String text = resp.body() == null ? "" : resp.body();
            if (text.length() > 2000) text = text.substring(0, 2000) + "…（已截断）";
            return "HTTP " + resp.statusCode() + "\n" + text;
        } catch (Exception e) {
            throw new BizException("HTTP 工具调用失败：" + e.getMessage());
        }
    }

    // ==================== 安全表达式求值（数字 + - * / ( ) ^） ====================

    public static final class SafeExpr {
        private final String s;
        private int i;

        private SafeExpr(String s) { this.s = s; }

        public static BigDecimal eval(String expr) {
            SafeExpr p = new SafeExpr(expr.replace(" ", ""));
            BigDecimal v = p.parseExpr();
            if (p.i < p.s.length()) throw new BizException("表达式含非法字符：" + expr);
            return v;
        }

        private BigDecimal parseExpr() {
            BigDecimal v = parseTerm();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                char op = s.charAt(i++);
                BigDecimal r = parseTerm();
                v = op == '+' ? v.add(r) : v.subtract(r);
            }
            return v;
        }

        private BigDecimal parseTerm() {
            BigDecimal v = parsePow();
            while (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                char op = s.charAt(i++);
                BigDecimal r = parsePow();
                v = op == '*' ? v.multiply(r) : v.divide(r, 10, RoundingMode.HALF_UP);
            }
            return v;
        }

        private BigDecimal parsePow() {
            BigDecimal base = parseFactor();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                BigDecimal exp = parseFactor();
                int e = exp.intValueExact();
                if (e < 0 || e > 64) throw new BizException("指数超出范围");
                return base.pow(e);
            }
            return base;
        }

        private BigDecimal parseFactor() {
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                BigDecimal v = parseExpr();
                if (i >= s.length() || s.charAt(i) != ')') throw new BizException("括号不匹配");
                i++;
                return v;
            }
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
                return parseFactor().negate();
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (start == i) throw new BizException("表达式解析失败");
            try {
                return new BigDecimal(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new BizException("数字格式错误");
            }
        }
    }
}
