package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.ai.entity.AiAuditLog;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 调用审计（R3 留痕）。每次 AI/规则调用写一条，绝不因审计失败阻塞业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final AiMappers.AuditLogMapper mapper;
    private final AiGuardrail guardrail;

    public void record(String scene, String aiMode, Long modelConfigId,
                       String input, String output,
                       Integer tokenIn, Integer tokenOut, BigDecimal cost,
                       Long durationMs, String status) {
        try {
            AiAuditLog l = new AiAuditLog();
            l.setScene(scene);
            l.setUserId(UserContext.currentUserId());
            l.setModelConfigId(modelConfigId);
            l.setInputDigest(guardrail.digest(input));
            l.setOutputDigest(guardrail.digest(output));
            l.setTokenIn(tokenIn);
            l.setTokenOut(tokenOut);
            l.setCost(cost);
            l.setDurationMs(durationMs);
            l.setAiMode(aiMode);
            l.setStatus(status);
            mapper.insert(l);
        } catch (Exception e) {
            log.error("[ai-audit] 写审计失败 scene={} err={}", scene, e.getMessage());
        }
    }

    public PageResult<AiAuditLog> page(long current, long size, String scene, String aiMode, String status) {
        LambdaQueryWrapper<AiAuditLog> qw = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isBlank()) qw.eq(AiAuditLog::getScene, scene);
        if (aiMode != null && !aiMode.isBlank()) qw.eq(AiAuditLog::getAiMode, aiMode);
        if (status != null && !status.isBlank()) qw.eq(AiAuditLog::getStatus, status);
        qw.orderByDesc(AiAuditLog::getId);
        Page<AiAuditLog> p = mapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<AiAuditLog> recent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AiAuditLog>()
                .orderByDesc(AiAuditLog::getId).last("LIMIT " + Math.max(1, limit)));
    }

    public AiAuditLog detail(Long id) {
        AiAuditLog l = mapper.selectById(id);
        if (l == null) throw new com.reip.common.exception.BizException("审计记录不存在");
        return l;
    }
}
