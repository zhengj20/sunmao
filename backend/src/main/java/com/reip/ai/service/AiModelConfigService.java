package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置：CRUD（密钥加密存储、展示脱敏）+ 测试连接（真实请求，不造假）。
 */
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiMappers.ModelConfigMapper mapper;
    private final AiLlmGateway gateway;
    private final AiAuditService audit;

    public PageResult<AiModelConfig> page(long current, long size, String provider, String status) {
        LambdaQueryWrapper<AiModelConfig> qw = new LambdaQueryWrapper<>();
        if (provider != null && !provider.isBlank()) qw.eq(AiModelConfig::getProvider, provider);
        if (status != null && !status.isBlank()) qw.eq(AiModelConfig::getStatus, status);
        qw.orderByAsc(AiModelConfig::getId);
        Page<AiModelConfig> p = mapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::mask);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<AiModelConfig> list() {
        List<AiModelConfig> all = mapper.selectList(new LambdaQueryWrapper<AiModelConfig>().orderByAsc(AiModelConfig::getId));
        all.forEach(this::mask);
        return all;
    }

    public AiModelConfig detail(Long id) {
        AiModelConfig c = require(id);
        mask(c);
        return c;
    }

    @Transactional
    public Long create(AiModelConfig c) {
        if (c == null) throw new BizException("缺少配置内容");
        if (c.getProvider() == null || c.getProvider().isBlank()) throw new BizException("请选择 provider");
        if (c.getModelName() == null || c.getModelName().isBlank()) throw new BizException("请填写模型名");
        if (c.getApiKey() != null && !c.getApiKey().isBlank()) c.setApiKey(gateway.encrypt(c.getApiKey()));
        if (c.getStatus() == null) c.setStatus("INACTIVE");
        if (c.getTimeoutMs() == null) c.setTimeoutMs(60000);
        c.setId(null);
        mapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void update(Long id, AiModelConfig c) {
        AiModelConfig cur = require(id);
        if (c.getProvider() != null) cur.setProvider(c.getProvider());
        if (c.getModelName() != null) cur.setModelName(c.getModelName());
        if (c.getBaseUrl() != null) cur.setBaseUrl(c.getBaseUrl());
        // 仅在提供新密钥时加密更新；空字符串表示清空
        if (c.getApiKey() != null && !c.getApiKey().isBlank()) cur.setApiKey(gateway.encrypt(c.getApiKey()));
        if (c.getTimeoutMs() != null) cur.setTimeoutMs(c.getTimeoutMs());
        if (c.getTemperature() != null) cur.setTemperature(c.getTemperature());
        if (c.getStatus() != null) cur.setStatus(c.getStatus());
        if (c.getRemark() != null) cur.setRemark(c.getRemark());
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    /** 测试连接：真实发一个最小请求，返回成功/失败 + 耗时。不造假。 */
    public Map<String, Object> test(Long id) {
        AiModelConfig cfg = require(id);
        AiLlmResult res = gateway.test(cfg);
        audit.record("MODEL_TEST", res.isSuccess() ? "LLM" : "RULE", cfg.getId(),
                "provider=" + cfg.getProvider() + " model=" + cfg.getModelName(),
                res.getMessage(), res.getTokenIn(), res.getTokenOut(), null, res.getDurationMs(),
                res.isSuccess() ? "SUCCESS" : "FAIL");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", res.isSuccess());
        out.put("message", res.getMessage());
        out.put("durationMs", res.getDurationMs());
        out.put("degraded", res.isDegraded());
        out.put("model", cfg.getModelName());
        return out;
    }

    private AiModelConfig require(Long id) {
        if (id == null) throw new BizException("缺少配置ID");
        AiModelConfig c = mapper.selectById(id);
        if (c == null) throw new BizException("模型配置不存在");
        return c;
    }

    private void mask(AiModelConfig c) {
        if (c.getApiKey() != null && !c.getApiKey().isBlank()) {
            c.setApiKeyMasked(gateway.mask(c.getApiKey()));
        } else {
            c.setApiKeyMasked(null);
        }
        c.setApiKey(null);
    }
}
