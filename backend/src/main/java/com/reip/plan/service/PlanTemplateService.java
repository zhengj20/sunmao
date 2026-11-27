package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.plan.entity.PlanNodeLib;
import com.reip.plan.entity.PlanTemplate;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 计划标准库：模板维护 + 节点库(WBS标准节点)维护
 */
@Service
@RequiredArgsConstructor
public class PlanTemplateService {

    private final PlanMappers.TemplateMapper templateMapper;
    private final PlanMappers.NodeLibMapper nodeLibMapper;

    // ==================== 模板 ====================

    public PageResult<PlanTemplate> page(long current, long size, String bizType, String status, String kw) {
        LambdaQueryWrapper<PlanTemplate> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) qw.eq(PlanTemplate::getBizType, bizType);
        if (status != null && !status.isBlank()) qw.eq(PlanTemplate::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(PlanTemplate::getName, kw);
        qw.orderByDesc(PlanTemplate::getId);
        Page<PlanTemplate> p = templateMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PlanTemplate detail(Long id) {
        PlanTemplate t = require(id);
        t.setNodes(nodeLibMapper.selectList(new LambdaQueryWrapper<PlanNodeLib>()
                .eq(PlanNodeLib::getTemplateId, id).orderByAsc(PlanNodeLib::getSortNo)));
        return t;
    }

    @Transactional
    public Long create(PlanTemplate t) {
        if (t == null) throw new BizException("缺少模板内容");
        if (t.getName() == null || t.getName().isBlank()) throw new BizException("请填写模板名称");
        t.setId(null);
        if (t.getStatus() == null) t.setStatus("ACTIVE");
        if (t.getTplVersion() == null) t.setTplVersion("V1");
        templateMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, PlanTemplate t) {
        PlanTemplate cur = require(id);
        if (t.getName() != null && !t.getName().isBlank()) cur.setName(t.getName());
        if (t.getBizType() != null) cur.setBizType(t.getBizType());
        if (t.getScale() != null) cur.setScale(t.getScale());
        if (t.getTplVersion() != null) cur.setTplVersion(t.getTplVersion());
        if (t.getStatus() != null) cur.setStatus(t.getStatus());
        if (templateMapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 节点库 ====================

    public List<PlanNodeLib> nodes(Long templateId) {
        require(templateId);
        return nodeLibMapper.selectList(new LambdaQueryWrapper<PlanNodeLib>()
                .eq(PlanNodeLib::getTemplateId, templateId).orderByAsc(PlanNodeLib::getSortNo));
    }

    @Transactional
    public Long addNode(Long templateId, PlanNodeLib n) {
        PlanTemplate tpl = require(templateId);
        if (n == null || n.getNodeName() == null || n.getNodeName().isBlank()) throw new BizException("请填写节点名称");
        if (n.getLevel() == null || n.getLevel().isBlank()) throw new BizException("请选择节点级次(里程碑/一级/二级/三级)");
        n.setTemplateId(tpl.getId());
        n.setId(null);
        if (n.getSortNo() == null) n.setSortNo(100);
        nodeLibMapper.insert(n);
        return n.getId();
    }

    @Transactional
    public void updateNode(Long nodeId, PlanNodeLib n) {
        PlanNodeLib cur = requireNode(nodeId);
        if (n.getNodeName() != null && !n.getNodeName().isBlank()) cur.setNodeName(n.getNodeName());
        if (n.getLevel() != null) cur.setLevel(n.getLevel());
        if (n.getBaseDuration() != null) cur.setBaseDuration(n.getBaseDuration());
        if (n.getRespLine() != null) cur.setRespLine(n.getRespLine());
        if (n.getAlertRule() != null) cur.setAlertRule(n.getAlertRule());
        if (n.getSortNo() != null) cur.setSortNo(n.getSortNo());
        if (nodeLibMapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        requireNode(nodeId);
        nodeLibMapper.deleteById(nodeId);
    }

    // ==================== 私有 ====================

    private PlanTemplate require(Long id) {
        if (id == null) throw new BizException("缺少模板ID");
        PlanTemplate t = templateMapper.selectById(id);
        if (t == null) throw new BizException("模板不存在");
        return t;
    }

    private PlanNodeLib requireNode(Long id) {
        if (id == null) throw new BizException("缺少节点ID");
        PlanNodeLib n = nodeLibMapper.selectById(id);
        if (n == null) throw new BizException("节点不存在");
        return n;
    }
}
