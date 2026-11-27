package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.DigEmployeeSkill;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.mapper.DigitalMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 技能管理：内置/市场/自定义，全部可编辑配置（提示词模板/取数域/说明）
 */
@Service
@RequiredArgsConstructor
public class DigitalSkillService {

    private final DigitalMappers.SkillMapper mapper;
    private final DigitalMappers.EmployeeSkillMapper esMapper;

    public List<DigSkill> list(String kw, String category) {
        LambdaQueryWrapper<DigSkill> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) {
            qw.and(w -> w.like(DigSkill::getName, kw.trim()).or().like(DigSkill::getDescription, kw.trim()));
        }
        if (category != null && !category.isBlank()) qw.eq(DigSkill::getCategory, category);
        qw.orderByAsc(DigSkill::getCategory).orderByAsc(DigSkill::getId);
        return mapper.selectList(qw);
    }

    public DigSkill detail(Long id) {
        DigSkill s = require(id);
        return s;
    }

    public List<DigSkill> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mapper.selectBatchIds(ids);
    }

    public Long create(DigSkill s) {
        if (s == null) throw new BizException("缺少技能内容");
        if (s.getSkillCode() == null || s.getSkillCode().isBlank()) throw new BizException("请填写技能编码");
        if (s.getName() == null || s.getName().isBlank()) throw new BizException("请填写技能名称");
        if (s.getCategory() == null) s.setCategory("CUSTOM");
        if (s.getDataSource() == null || s.getDataSource().isBlank()) s.setDataSource("NONE");
        if (s.getPromptTemplate() == null || s.getPromptTemplate().isBlank()) {
            throw new BizException("请填写提示词模板（支持 {{data}} {{taskName}} 占位符）");
        }
        s.setId(null);
        s.setInstallCount(0);
        if (s.getStatus() == null) s.setStatus("ACTIVE");
        mapper.insert(s);
        return s.getId();
    }

    public void update(Long id, DigSkill s) {
        DigSkill cur = require(id);
        if (s.getName() != null) cur.setName(s.getName());
        if (s.getCategory() != null) cur.setCategory(s.getCategory());
        if (s.getDataSource() != null) cur.setDataSource(s.getDataSource());
        if (s.getIcon() != null) cur.setIcon(s.getIcon());
        if (s.getDescription() != null) cur.setDescription(s.getDescription());
        if (s.getPromptTemplate() != null && !s.getPromptTemplate().isBlank()) cur.setPromptTemplate(s.getPromptTemplate());
        if (s.getSource() != null) cur.setSource(s.getSource());
        if (s.getStatus() != null) cur.setStatus(s.getStatus());
        if (s.getKbSearch() != null) cur.setKbSearch(s.getKbSearch());
        cur.setToolIds(s.getToolIds());
        cur.setToolArgs(s.getToolArgs());
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    public void delete(Long id) {
        DigSkill s = require(id);
        if ("BUILTIN".equals(s.getCategory())) throw new BizException("内置技能不可删除（可停用/编辑）");
        // 同步移除员工-技能关联
        esMapper.delete(new LambdaQueryWrapper<DigEmployeeSkill>().eq(DigEmployeeSkill::getSkillId, id));
        mapper.deleteById(id);
    }

    /** 安装到员工（幂等；installCount 仅统计本次新增的安装） */
    @Transactional
    public void installTo(Long skillId, List<Long> employeeIds) {
        DigSkill s = require(skillId);
        if (employeeIds == null || employeeIds.isEmpty()) throw new BizException("请选择要安装到的员工");
        int added = 0;
        for (Long eid : employeeIds) {
            if (eid == null) continue;
            Long exists = esMapper.selectCount(new LambdaQueryWrapper<DigEmployeeSkill>()
                    .eq(DigEmployeeSkill::getEmployeeId, eid).eq(DigEmployeeSkill::getSkillId, skillId));
            if (exists == null || exists == 0) {
                DigEmployeeSkill es = new DigEmployeeSkill();
                es.setEmployeeId(eid);
                es.setSkillId(skillId);
                es.setSort(0);
                esMapper.insert(es);
                added++;
            }
        }
        if (added > 0) {
            s.setInstallCount((s.getInstallCount() == null ? 0 : s.getInstallCount()) + added);
            mapper.updateById(s);
        }
    }

    private DigSkill require(Long id) {
        if (id == null) throw new BizException("缺少技能ID");
        DigSkill s = mapper.selectById(id);
        if (s == null) throw new BizException("技能不存在");
        return s;
    }
}
