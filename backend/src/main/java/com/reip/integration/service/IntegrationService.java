package com.reip.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.integration.entity.IntAdapterConfig;
import com.reip.integration.entity.IntApiRegistry;
import com.reip.integration.entity.IntAppKey;
import com.reip.integration.entity.IntCallLog;
import com.reip.integration.mapper.IntegrationMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成平台：API注册 / 适配器 / 调用日志 / 调用方密钥
 */
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final IntegrationMappers.ApiRegistryMapper apiMapper;
    private final IntegrationMappers.AdapterMapper adapterMapper;
    private final IntegrationMappers.AppKeyMapper appKeyMapper;
    private final IntegrationMappers.CallLogMapper callLogMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ================= API注册 =================

    public PageResult<IntApiRegistry> apiPage(long current, long size, String apiCode, String status) {
        LambdaQueryWrapper<IntApiRegistry> qw = new LambdaQueryWrapper<>();
        if (apiCode != null && !apiCode.isBlank()) qw.like(IntApiRegistry::getApiCode, apiCode);
        if (status != null && !status.isBlank()) qw.eq(IntApiRegistry::getStatus, status);
        qw.orderByDesc(IntApiRegistry::getId);
        Page<IntApiRegistry> p = apiMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public IntApiRegistry apiDetail(Long id) {
        IntApiRegistry api = apiMapper.selectById(id);
        if (api == null) throw new BizException("API不存在");
        return api;
    }

    @Transactional
    public Long createApi(IntApiRegistry api) {
        Long cnt = apiMapper.selectCount(new LambdaQueryWrapper<IntApiRegistry>()
                .eq(IntApiRegistry::getApiCode, api.getApiCode()));
        if (cnt > 0) throw new BizException("API编码已存在：" + api.getApiCode());
        api.setId(null);
        if (api.getStatus() == null || api.getStatus().isBlank()) api.setStatus("ENABLED");
        apiMapper.insert(api);
        return api.getId();
    }

    @Transactional
    public void updateApi(IntApiRegistry api) {
        apiDetail(api.getId());
        api.setApiCode(null); // 编码不允许修改
        int updated = apiMapper.updateById(api);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteApi(Long id) {
        apiDetail(id);
        apiMapper.deleteById(id);
    }

    // ================= 适配器 =================

    public PageResult<IntAdapterConfig> adapterPage(long current, long size, String adapterCode, String adapterType, String status) {
        LambdaQueryWrapper<IntAdapterConfig> qw = new LambdaQueryWrapper<>();
        if (adapterCode != null && !adapterCode.isBlank()) qw.like(IntAdapterConfig::getAdapterCode, adapterCode);
        if (adapterType != null && !adapterType.isBlank()) qw.eq(IntAdapterConfig::getAdapterType, adapterType);
        if (status != null && !status.isBlank()) qw.eq(IntAdapterConfig::getStatus, status);
        qw.orderByDesc(IntAdapterConfig::getId);
        Page<IntAdapterConfig> p = adapterMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public IntAdapterConfig adapterDetail(Long id) {
        IntAdapterConfig adapter = adapterMapper.selectById(id);
        if (adapter == null) throw new BizException("适配器不存在");
        return adapter;
    }

    @Transactional
    public Long createAdapter(IntAdapterConfig adapter) {
        Long cnt = adapterMapper.selectCount(new LambdaQueryWrapper<IntAdapterConfig>()
                .eq(IntAdapterConfig::getAdapterCode, adapter.getAdapterCode()));
        if (cnt > 0) throw new BizException("适配器编码已存在：" + adapter.getAdapterCode());
        adapter.setId(null);
        if (adapter.getStatus() == null || adapter.getStatus().isBlank()) adapter.setStatus("ENABLED");
        if (adapter.getSyncDirection() == null || adapter.getSyncDirection().isBlank()) adapter.setSyncDirection("BOTH");
        adapterMapper.insert(adapter);
        return adapter.getId();
    }

    @Transactional
    public void updateAdapter(IntAdapterConfig adapter) {
        adapterDetail(adapter.getId());
        adapter.setAdapterCode(null); // 编码不允许修改
        int updated = adapterMapper.updateById(adapter);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteAdapter(Long id) {
        adapterDetail(id);
        adapterMapper.deleteById(id);
    }

    /** 测试连接：从 configJson 解析可测出站地址→真实 GET 外呼，写真实调用日志；无可测地址则记失败日志 */
    @Transactional
    public Map<String, Object> testAdapter(Long id) {
        IntAdapterConfig adapter = adapterDetail(id);
        String url = resolveTestUrl(adapter);

        if (url == null || url.isBlank()) {
            IntCallLog log = new IntCallLog();
            log.setAdapterCode(adapter.getAdapterCode());
            log.setDirection("OUT");
            log.setRequestDigest("{\"action\":\"testConnection\",\"url\":null}");
            log.setResponseDigest("未配置可测试的出站地址");
            log.setSuccess(0);
            log.setCostMs(0L);
            log.setErrorMsg("未配置可测试的出站地址（configJson 缺少 host/baseUrl/url 字段）");
            log.setCreatedAt(LocalDateTime.now());
            callLogMapper.insert(log);
            return testResult(0, 0L, -1, "未配置可测试的出站地址");
        }

        long start = System.currentTimeMillis();
        int httpStatus = -1;
        String body = "";
        String error = null;
        try {
            HttpResponse<String> resp = HTTP_CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    BodyHandlers.ofString());
            httpStatus = resp.statusCode();
            body = resp.body() == null ? "" : resp.body();
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        long costMs = System.currentTimeMillis() - start;
        boolean ok = httpStatus >= 200 && httpStatus < 400;

        IntCallLog log = new IntCallLog();
        log.setAdapterCode(adapter.getAdapterCode());
        log.setDirection("OUT");
        log.setRequestDigest("{\"action\":\"testConnection\",\"url\":\"" + escapeJson(url) + "\"}");
        log.setResponseDigest(truncate(httpStatus >= 0 ? ("[HTTP " + httpStatus + "] " + body)
                : ("[网络异常] " + (error == null ? "连接失败或超时" : error)), 512));
        log.setSuccess(ok ? 1 : 0);
        log.setCostMs(costMs);
        log.setErrorMsg(ok ? null : (httpStatus >= 0 ? "HTTP " + httpStatus : (error == null ? "网络异常或超时" : error)));
        log.setCreatedAt(LocalDateTime.now());
        callLogMapper.insert(log);

        IntAdapterConfig up = new IntAdapterConfig();
        up.setId(id);
        up.setVersion(adapter.getVersion());
        up.setLastSyncAt(LocalDateTime.now());
        adapterMapper.updateById(up);

        String message = ok
                ? "连接成功（HTTP " + httpStatus + "，耗时 " + costMs + "ms）"
                : (httpStatus >= 0
                        ? "连接失败（HTTP " + httpStatus + "，耗时 " + costMs + "ms）"
                        : "连接失败（网络异常或超时" + (error == null ? "" : "：" + error) + "）");
        return testResult(ok ? 1 : 0, costMs, httpStatus, message);
    }

    private Map<String, Object> testResult(int success, long costMs, int httpStatus, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("costMs", costMs);
        result.put("httpStatus", httpStatus);
        result.put("message", message);
        return result;
    }

    /** 约定：configJson 中取 testUrl/baseUrl/base_url/url/endpoint/host 作为基础地址，可选拼接 path */
    private String resolveTestUrl(IntAdapterConfig adapter) {
        String configJson = adapter.getConfigJson();
        if (configJson == null || configJson.isBlank()) return null;
        try {
            JsonNode node = OBJECT_MAPPER.readTree(configJson);
            String base = firstText(node, "testUrl", "baseUrl", "base_url", "url", "endpoint", "host");
            if (base == null || base.isBlank()) return null;
            String path = firstText(node, "path");
            if (path != null && !path.isBlank()) {
                if (base.endsWith("/") && path.startsWith("/")) path = path.substring(1);
                if (!base.endsWith("/") && !path.startsWith("/")) base = base + "/";
                base = base + path;
            }
            return base;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && node.get(key).isTextual()) {
                String v = node.get(key).asText();
                if (!v.isBlank()) return v;
            }
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ================= 调用日志 =================

    public PageResult<IntCallLog> logPage(long current, long size, String adapterCode, Integer success) {
        LambdaQueryWrapper<IntCallLog> qw = new LambdaQueryWrapper<>();
        if (adapterCode != null && !adapterCode.isBlank()) qw.eq(IntCallLog::getAdapterCode, adapterCode);
        if (success != null) qw.eq(IntCallLog::getSuccess, success);
        qw.orderByDesc(IntCallLog::getId);
        Page<IntCallLog> p = callLogMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 按适配器统计成功数/失败数/平均耗时 */
    public List<Map<String, Object>> logStats() {
        return callLogMapper.selectStats();
    }

    // ================= 调用方密钥 =================

    public PageResult<IntAppKey> appKeyPage(long current, long size, String appName, String status) {
        LambdaQueryWrapper<IntAppKey> qw = new LambdaQueryWrapper<>();
        if (appName != null && !appName.isBlank()) qw.like(IntAppKey::getAppName, appName);
        if (status != null && !status.isBlank()) qw.eq(IntAppKey::getStatus, status);
        qw.orderByDesc(IntAppKey::getId);
        Page<IntAppKey> p = appKeyMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public IntAppKey appKeyDetail(Long id) {
        IntAppKey key = appKeyMapper.selectById(id);
        if (key == null) throw new BizException("密钥不存在");
        return key;
    }

    @Transactional
    public Long createAppKey(IntAppKey appKey) {
        Long cnt = appKeyMapper.selectCount(new LambdaQueryWrapper<IntAppKey>()
                .eq(IntAppKey::getAppKey, appKey.getAppKey()));
        if (cnt > 0) throw new BizException("应用Key已存在：" + appKey.getAppKey());
        appKey.setId(null);
        if (appKey.getStatus() == null || appKey.getStatus().isBlank()) appKey.setStatus("ENABLED");
        appKeyMapper.insert(appKey);
        return appKey.getId();
    }

    @Transactional
    public void updateAppKey(IntAppKey appKey) {
        appKeyDetail(appKey.getId());
        appKey.setAppKey(null); // Key不允许修改
        int updated = appKeyMapper.updateById(appKey);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteAppKey(Long id) {
        appKeyDetail(id);
        appKeyMapper.deleteById(id);
    }
}
