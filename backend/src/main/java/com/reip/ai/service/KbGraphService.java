package com.reip.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.ai.entity.AiKbDocument;
import com.reip.ai.entity.KbLink;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KbGraphService {

    private final AiMappers.KbDocumentMapper docMapper;
    private final AiMappers.KbLinkMapper linkMapper;

    /** 获取知识图谱数据（节点+边） */
    public Map<String, Object> graph() {
        List<AiKbDocument> docs = docMapper.selectList(new LambdaQueryWrapper<AiKbDocument>()
                .eq(AiKbDocument::getStatus, "ACTIVE"));
        List<KbLink> links = linkMapper.selectList(null);

        // 只保留两端都存在的边
        Set<Long> docIds = docs.stream().map(AiKbDocument::getId).collect(Collectors.toSet());
        List<KbLink> validLinks = links.stream()
                .filter(l -> docIds.contains(l.getSourceDocId()) && docIds.contains(l.getTargetDocId()))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", docs.stream().map(d -> {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", d.getId());
            node.put("docCode", d.getDocCode());
            node.put("title", d.getTitle());
            node.put("category", d.getCategory());
            node.put("wikiPath", d.getWikiPath());
            return node;
        }).toList());
        result.put("edges", validLinks.stream().map(l -> {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", l.getSourceDocId());
            edge.put("target", l.getTargetDocId());
            edge.put("relationType", l.getRelationType());
            edge.put("label", l.getLabel());
            return edge;
        }).toList());
        return result;
    }

    /** 获取某文档的关联文档 */
    public List<Map<String, Object>> related(Long docId) {
        AiKbDocument doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException("文档不存在");

        List<KbLink> outLinks = linkMapper.selectList(new LambdaQueryWrapper<KbLink>()
                .eq(KbLink::getSourceDocId, docId));
        List<KbLink> inLinks = linkMapper.selectList(new LambdaQueryWrapper<KbLink>()
                .eq(KbLink::getTargetDocId, docId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (KbLink l : outLinks) {
            AiKbDocument target = docMapper.selectById(l.getTargetDocId());
            if (target != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("doc", target);
                item.put("relation", l.getRelationType());
                item.put("label", l.getLabel());
                item.put("direction", "out");
                result.add(item);
            }
        }
        for (KbLink l : inLinks) {
            AiKbDocument source = docMapper.selectById(l.getSourceDocId());
            if (source != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("doc", source);
                item.put("relation", l.getRelationType());
                item.put("label", l.getLabel());
                item.put("direction", "in");
                result.add(item);
            }
        }
        return result;
    }

    /** 添加关联 */
    @Transactional
    public void addLink(Long sourceDocId, Long targetDocId, String relationType, String label) {
        if (sourceDocId.equals(targetDocId)) throw new BizException("不能关联自身");
        if (docMapper.selectById(sourceDocId) == null) throw new BizException("源文档不存在");
        if (docMapper.selectById(targetDocId) == null) throw new BizException("目标文档不存在");

        // 检查是否已存在
        KbLink existing = linkMapper.selectOne(new LambdaQueryWrapper<KbLink>()
                .eq(KbLink::getSourceDocId, sourceDocId).eq(KbLink::getTargetDocId, targetDocId));
        if (existing != null) throw new BizException("关联已存在");

        KbLink link = new KbLink();
        link.setSourceDocId(sourceDocId);
        link.setTargetDocId(targetDocId);
        link.setRelationType(relationType != null ? relationType : "RELATED");
        link.setLabel(label);
        linkMapper.insert(link);
    }

    /** 删除关联 */
    public void removeLink(Long sourceDocId, Long targetDocId) {
        KbLink existing = linkMapper.selectOne(new LambdaQueryWrapper<KbLink>()
                .eq(KbLink::getSourceDocId, sourceDocId).eq(KbLink::getTargetDocId, targetDocId));
        if (existing != null) {
            linkMapper.deleteById(existing.getId());
        }
    }

    /** Wiki 文档列表（按 wiki_path 分组排序） */
    public List<AiKbDocument> wikiList() {
        return docMapper.selectList(new LambdaQueryWrapper<AiKbDocument>()
                .eq(AiKbDocument::getStatus, "ACTIVE")
                .isNotNull(AiKbDocument::getWikiPath)
                .orderByAsc(AiKbDocument::getWikiPath)
                .orderByAsc(AiKbDocument::getWikiOrder));
    }
}
