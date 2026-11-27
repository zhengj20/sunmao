package com.reip.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.workflow.entity.WflInstance;
import com.reip.workflow.entity.WflTemplate;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程模板管理
 */
@Service
@RequiredArgsConstructor
public class WflTemplateService {

    private final WorkflowMappers.TemplateMapper templateMapper;
    private final WorkflowMappers.InstanceMapper instanceMapper;
    private final ObjectMapper objectMapper;

    public PageResult<WflTemplate> page(long current, long size, String kw) {
        LambdaQueryWrapper<WflTemplate> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(WflTemplate::getName, kw)
                .or().like(WflTemplate::getTemplateCode, kw).or().like(WflTemplate::getBizType, kw));
        qw.orderByDesc(WflTemplate::getId);
        Page<WflTemplate> p = templateMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 读取模板（含 definition JSON，设计器编辑用） */
    public WflTemplate detail(Long id) {
        WflTemplate t = templateMapper.selectById(id);
        if (t == null) throw new BizException("模板不存在");
        return t;
    }

    /** 保存模板：新增校验 biz_type 无 ACTIVE 模板；编辑不允许改 biz_type */
    @Transactional
    public Long save(WflTemplate tpl) {
        if (tpl.getBizType() == null || tpl.getBizType().isBlank()) throw new BizException("请指定模板绑定的单据类型");
        if (tpl.getDefinition() == null || tpl.getDefinition().isBlank()) throw new BizException("请填写流程定义");
        try {
            objectMapper.readTree(tpl.getDefinition());
        } catch (Exception e) {
            throw new BizException("流程定义不是合法的JSON");
        }
        if (tpl.getId() == null) {
            Long cnt = templateMapper.selectCount(new LambdaQueryWrapper<WflTemplate>()
                    .eq(WflTemplate::getBizType, tpl.getBizType())
                    .eq(WflTemplate::getStatus, "ACTIVE"));
            if (cnt > 0) throw new BizException("该单据类型已有生效模板，请先归档");
            tpl.setStatus("ACTIVE");
            if (tpl.getTplVersion() == null) tpl.setTplVersion(1);
            templateMapper.insert(tpl);
        } else {
            WflTemplate old = templateMapper.selectById(tpl.getId());
            if (old == null) throw new BizException("模板不存在");
            if (!old.getBizType().equals(tpl.getBizType())) throw new BizException("不允许修改模板绑定的单据类型");
            tpl.setBizType(null); // biz_type 不参与更新
            tpl.setStatus(null); // 状态仅通过归档接口变更
            int updated = templateMapper.updateById(tpl);
            if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }
        return tpl.getId();
    }

    /** 归档：该 bizType 有 RUNNING 实例则禁止 */
    @Transactional
    public void archive(Long id) {
        WflTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) throw new BizException("模板不存在");
        Long running = instanceMapper.selectCount(new LambdaQueryWrapper<WflInstance>()
                .eq(WflInstance::getBizType, tpl.getBizType())
                .eq(WflInstance::getStatus, "RUNNING"));
        if (running > 0) throw new BizException("该单据类型存在在途流程，禁止归档");
        WflTemplate up = new WflTemplate();
        up.setId(id);
        up.setVersion(tpl.getVersion());
        up.setStatus("ARCHIVED");
        if (templateMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }
}
