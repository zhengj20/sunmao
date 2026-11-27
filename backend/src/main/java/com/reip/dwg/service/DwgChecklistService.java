package com.reip.dwg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.dwg.entity.DwgChecklist;
import com.reip.dwg.mapper.DwgMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 图纸审查要点库：AI 预审「审查要点」的可配置规则源。
 * 支持按专业/严重度检索，增删改与启停。
 */
@Service
@RequiredArgsConstructor
public class DwgChecklistService {

    private final DwgMappers.ChecklistMapper checklistMapper;

    public PageResult<DwgChecklist> page(long current, long size, String discipline, String level, String kw) {
        LambdaQueryWrapper<DwgChecklist> qw = new LambdaQueryWrapper<>();
        if (discipline != null && !discipline.isBlank()) qw.eq(DwgChecklist::getDiscipline, discipline.trim().toUpperCase(Locale.ROOT));
        if (level != null && !level.isBlank()) qw.eq(DwgChecklist::getLevel, level.trim().toUpperCase(Locale.ROOT));
        if (kw != null && !kw.isBlank()) qw.like(DwgChecklist::getDescription, kw.trim());
        qw.orderByAsc(DwgChecklist::getSort).orderByAsc(DwgChecklist::getId);
        Page<DwgChecklist> p = checklistMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<DwgChecklist> listAll() {
        return checklistMapper.selectList(new LambdaQueryWrapper<DwgChecklist>()
                .orderByAsc(DwgChecklist::getSort).orderByAsc(DwgChecklist::getId));
    }

    @Transactional
    public DwgChecklist add(Form form) {
        validate(form);
        DwgChecklist c = new DwgChecklist();
        apply(c, form);
        checklistMapper.insert(c);
        return c;
    }

    @Transactional
    public DwgChecklist update(Long id, Form form) {
        DwgChecklist c = require(id);
        apply(c, form);
        if (checklistMapper.updateById(c) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return c;
    }

    @Transactional
    public void delete(Long id) {
        checklistMapper.deleteById(id);
    }

    /** 启用/停用切换 */
    @Transactional
    public DwgChecklist toggle(Long id) {
        DwgChecklist c = require(id);
        c.setEnabled(c.getEnabled() == null || c.getEnabled() == 1 ? 0 : 1);
        if (checklistMapper.updateById(c) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return c;
    }

    private void apply(DwgChecklist c, Form form) {
        c.setDiscipline(form.getDiscipline() == null || form.getDiscipline().isBlank()
                ? "ALL" : form.getDiscipline().trim().toUpperCase(Locale.ROOT));
        c.setStage(blank(form.getStage()));
        c.setLevel(form.getLevel() == null || form.getLevel().isBlank() ? "MEDIUM" : form.getLevel().trim().toUpperCase(Locale.ROOT));
        c.setCategory(blank(form.getCategory()));
        c.setDescription(form.getDescription());
        c.setSuggestion(blank(form.getSuggestion()));
        c.setEnabled(form.getEnabled() == null ? 1 : form.getEnabled());
        c.setSort(form.getSort() == null ? 0 : form.getSort());
    }

    private void validate(Form form) {
        if (form.getDescription() == null || form.getDescription().isBlank()) throw new BizException("请填写审查要点描述");
    }

    private DwgChecklist require(Long id) {
        if (id == null) throw new BizException("缺少要点ID");
        DwgChecklist c = checklistMapper.selectById(id);
        if (c == null) throw new BizException("审查要点不存在");
        return c;
    }

    private String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** 审查要点新增/编辑表单 */
    @Data
    public static class Form {
        private String discipline;
        private String stage;
        private String level;
        private String category;
        private String description;
        private String suggestion;
        private Integer enabled;
        private Integer sort;
    }
}
