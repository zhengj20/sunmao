package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.ai.entity.AiKbDocument;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库：CRUD + 检索（关键词匹配，embedding 留占位）。
 */
@Service
@RequiredArgsConstructor
public class AiKbService {

    private final AiMappers.KbDocumentMapper mapper;

    public PageResult<AiKbDocument> page(long current, long size, String category, String kw) {
        LambdaQueryWrapper<AiKbDocument> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) qw.eq(AiKbDocument::getCategory, category);
        if (kw != null && !kw.isBlank()) {
            qw.and(w -> w.like(AiKbDocument::getTitle, kw).or().like(AiKbDocument::getContent, kw));
        }
        qw.orderByDesc(AiKbDocument::getId);
        Page<AiKbDocument> p = mapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 检索：标题/内容包含关键词，返回前 N 条 */
    public List<AiKbDocument> search(String kw, int limit) {
        if (kw == null || kw.isBlank()) throw new BizException("请输入检索关键词");
        List<AiKbDocument> all = mapper.selectList(new LambdaQueryWrapper<AiKbDocument>()
                .and(w -> w.like(AiKbDocument::getTitle, kw).or().like(AiKbDocument::getContent, kw))
                .orderByDesc(AiKbDocument::getId)
                .last("LIMIT " + Math.max(1, Math.min(50, limit))));
        return all;
    }

    /**
     * 内部检索（不抛异常）：用于数字员工/AI助手/审批/报告 注入上下文。
     * 按空格分词，每个词分别 LIKE 匹配标题或内容，取并集去重后返回前 limit 条。
     */
    public List<AiKbDocument> searchForContext(String keywords, String category, int limit) {
        if (keywords == null || keywords.isBlank()) return List.of();
        int n = Math.max(1, Math.min(20, limit));
        LambdaQueryWrapper<AiKbDocument> qw = new LambdaQueryWrapper<>();
        qw.eq(AiKbDocument::getStatus, "ACTIVE");
        if (category != null && !category.isBlank()) qw.eq(AiKbDocument::getCategory, category);
        String[] words = keywords.split("[\\s,，]+");
        qw.and(w -> {
            for (int i = 0; i < words.length; i++) {
                String kw = words[i].trim();
                if (kw.isEmpty()) continue;
                if (i == 0) w.like(AiKbDocument::getTitle, kw).or().like(AiKbDocument::getContent, kw);
                else w.or().like(AiKbDocument::getTitle, kw).or().like(AiKbDocument::getContent, kw);
            }
        });
        qw.orderByDesc(AiKbDocument::getId).last("LIMIT " + n);
        try {
            return mapper.selectList(qw);
        } catch (Exception e) {
            return List.of();
        }
    }

    public AiKbDocument detail(Long id) {
        AiKbDocument d = mapper.selectById(id);
        if (d == null) throw new BizException("文档不存在");
        return d;
    }

    /** 按ID批量取有效文档（数字员工/工作流 知识库绑定注入用；保持传入顺序） */
    public List<AiKbDocument> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<AiKbDocument> all = mapper.selectList(new LambdaQueryWrapper<AiKbDocument>()
                .in(AiKbDocument::getId, ids).eq(AiKbDocument::getStatus, "ACTIVE"));
        Map<Long, AiKbDocument> m = new HashMap<>();
        for (AiKbDocument d : all) m.put(d.getId(), d);
        List<AiKbDocument> out = new ArrayList<>();
        for (Long id : ids) {
            AiKbDocument d = m.get(id);
            if (d != null) out.add(d);
        }
        return out;
    }

    @Transactional
    public Long create(AiKbDocument d) {
        if (d == null) throw new BizException("缺少文档内容");
        if (d.getDocCode() == null || d.getDocCode().isBlank()) d.setDocCode("KB-" + System.currentTimeMillis());
        if (mapper.selectCount(new LambdaQueryWrapper<AiKbDocument>()
                .eq(AiKbDocument::getDocCode, d.getDocCode())) > 0) {
            throw new BizException("文档编码已存在：" + d.getDocCode());
        }
        if (d.getTitle() == null || d.getTitle().isBlank()) throw new BizException("请填写标题");
        if (d.getStatus() == null) d.setStatus("ACTIVE");
        if (d.getEmbeddingReady() == null) d.setEmbeddingReady(0);
        d.setId(null);
        mapper.insert(d);
        return d.getId();
    }

    @Transactional
    public void update(Long id, AiKbDocument d) {
        AiKbDocument cur = detail(id);
        if (d.getTitle() != null) cur.setTitle(d.getTitle());
        if (d.getCategory() != null) cur.setCategory(d.getCategory());
        if (d.getContent() != null) cur.setContent(d.getContent());
        if (d.getStatus() != null) cur.setStatus(d.getStatus());
        if (d.getEmbeddingReady() != null) cur.setEmbeddingReady(d.getEmbeddingReady());
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        mapper.deleteById(id);
    }
}
