package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.entity.AiPrompt;
import com.reip.ai.mapper.AiMappers;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;

/**
 * 统一 LLM 网关（OpenAI 兼容 chat/completions 协议）。
 *
 * 降级策略（R4）：
 *  - 无 ACTIVE 配置 / 调用超时 / 网络异常 / 非 2xx → 返回 degraded 结果，绝不抛错阻塞业务；
 *  - 调用方据 degraded 标记回退规则引擎，ai_mode 记 RULE。
 *
 * 密钥安全：AES/GCM 加密存储，展示脱敏，日志不打印完整 key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLlmGateway {

    private static final String ENC_PREFIX = "ENC:";

    /**
     * LLM API Key 入库加密主密钥，来自 reip.ai.master-key（环境变量 AI_KEY_MASTER_KEY）。
     * 不再内置默认值：源码公开后内置密钥等同于明文。
     * 未配置时仅禁用「模型配置」的 Key 存取，其余模块按 R4 降级策略正常运行。
     */
    @Value("${reip.ai.master-key:}")
    private String masterKey;

    private byte[] aesKey;

    @PostConstruct
    void initMasterKey() {
        if (masterKey == null || masterKey.trim().isEmpty()) {
            log.warn("未配置 reip.ai.master-key（环境变量 AI_KEY_MASTER_KEY），AI 模型 API Key 将无法保存");
            return;
        }
        try {
            aesKey = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final int GCM_IV = 12;
    private static final int GCM_TAG = 128;

    private final AiMappers.ModelConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    // ==================== 配置查询 ====================

    public AiModelConfig findActive() {
        return configMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getStatus, "ACTIVE")
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT 1"));
    }

    // ==================== 对话 ====================

    /**
     * 用提示词模板 + 上下文调用 LLM（自动取系统默认激活模型）。
     * 无模型/失败时返回 degraded=true（不抛错）。
     */
    public AiLlmResult chat(AiPrompt prompt, Map<String, Object> context) {
        AiModelConfig cfg = findActive();
        if (cfg == null) {
            return AiLlmResult.none();
        }
        if (prompt == null || prompt.getTemplateText() == null) {
            return AiLlmResult.fail("缺少提示词模板");
        }
        String system = fillTemplate(prompt.getTemplateText(), context);
        String user = safeJson(context);
        return chatWith(cfg, system, user);
    }

    /**
     * 用指定模型配置调用 LLM（数字员工绑定指定模型时使用）。
     * cfg 为 null 时取系统默认激活模型；失败返回 degraded=true（不抛错）。
     */
    public AiLlmResult chatWith(AiModelConfig cfg, String system, String user) {
        if (cfg == null) {
            cfg = findActive();
        }
        if (cfg == null) {
            return AiLlmResult.none();
        }
        long t0 = System.currentTimeMillis();
        try {
            String url = resolveChatUrl(cfg);
            String body = buildBody(cfg.getModelName(), system, user, temperature(cfg));
            HttpResponse<String> resp = httpPost(url, apiKey(cfg), timeoutMs(cfg), body);
            long cost = System.currentTimeMillis() - t0;
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("[llm] 调用失败 status={} model={}", status, cfg.getModelName());
                return AiLlmResult.fail("模型返回 " + status);
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int tin = (int) root.path("usage").path("prompt_tokens").asInt(0);
            int tout = (int) root.path("usage").path("completion_tokens").asInt(0);
            if (content == null || content.isBlank()) {
                return AiLlmResult.fail("模型返回内容为空");
            }
            return AiLlmResult.ok(content, cfg.getModelName(), tin, tout, cost);
        } catch (Exception e) {
            log.warn("[llm] 调用异常 model={} err={}", cfg.getModelName(), e.getMessage());
            return AiLlmResult.fail("模型调用异常：" + e.getMessage());
        }
    }

    /**
     * 测试连接：真实发一个最小请求，返回成功/失败 + 耗时。不造假。
     */
    public AiLlmResult test(AiModelConfig cfg) {
        if (cfg == null) return AiLlmResult.none();
        if (apiKey(cfg) == null || apiKey(cfg).isBlank()) {
            return AiLlmResult.fail("未配置 API Key");
        }
        long t0 = System.currentTimeMillis();
        try {
            String url = resolveChatUrl(cfg);
            String body = buildBody(cfg.getModelName(), "You are a helpful assistant.", "ping", 0.1);
            HttpResponse<String> resp = httpPost(url, apiKey(cfg), timeoutMs(cfg), body);
            long cost = System.currentTimeMillis() - t0;
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(resp.body());
                int tin = (int) root.path("usage").path("prompt_tokens").asInt(0);
                int tout = (int) root.path("usage").path("completion_tokens").asInt(0);
                return AiLlmResult.ok("连接成功", cfg.getModelName(), tin, tout, cost);
            }
            return AiLlmResult.fail("HTTP " + resp.statusCode());
        } catch (Exception e) {
            return AiLlmResult.fail(e.getMessage());
        }
    }

    // ==================== 密钥加解密（脱敏） ====================

    public String encrypt(String plain) {
        if (plain == null) return null;
        if (aesKey == null) throw new IllegalStateException("未配置 reip.ai.master-key，无法保存 API Key");
        try {
            byte[] iv = new byte[GCM_IV];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(ENC_PREFIX)) return stored; // 兼容明文占位
        if (aesKey == null) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[GCM_IV];
            System.arraycopy(raw, 0, iv, 0, GCM_IV);
            byte[] ct = new byte[raw.length - GCM_IV];
            System.arraycopy(raw, GCM_IV, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 脱敏展示：前2后2，中间 **** */
    public String mask(String stored) {
        String plain = decrypt(stored);
        if (plain == null || plain.length() <= 4) return "****";
        return plain.substring(0, 2) + "****" + plain.substring(plain.length() - 2);
    }

    // ==================== 内部 ====================

    private String resolveChatUrl(AiModelConfig cfg) {
        String base = cfg.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = switch (cfg.getProvider() == null ? "" : cfg.getProvider().toUpperCase()) {
                case "QWEN" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
                case "DEEPSEEK" -> "https://api.deepseek.com/v1";
                default -> "https://api.openai.com/v1";
            };
        }
        base = base.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        return base + "/chat/completions";
    }

    private String buildBody(String model, String system, String user, double temp) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temp);
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        });
        return objectMapper.writeValueAsString(body);
    }

    private HttpResponse<String> httpPost(String url, String key, int timeoutMs, String body) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String fillTemplate(String template, Map<String, Object> ctx) {
        if (ctx == null) return template;
        String out = template;
        for (Map.Entry<String, Object> e : ctx.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return out;
    }

    private String safeJson(Map<String, Object> ctx) {
        try {
            return ctx == null ? "{}" : objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            return String.valueOf(ctx);
        }
    }

    private String apiKey(AiModelConfig cfg) {
        return decrypt(cfg.getApiKey());
    }

    private int timeoutMs(AiModelConfig cfg) {
        return cfg.getTimeoutMs() == null ? 60000 : cfg.getTimeoutMs();
    }

    private double temperature(AiModelConfig cfg) {
        return cfg.getTemperature() == null ? 0.7 : cfg.getTemperature().doubleValue();
    }
}
