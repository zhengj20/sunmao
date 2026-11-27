package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.proc.entity.ProcExpert;
import com.reip.proc.mapper.ProcMappers.ExpertMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 评标专家库：CRUD + 抽取（ACTIVE 且 avoid_orgs 不含回避关键词中随机取 N 名）
 */
@Service
@RequiredArgsConstructor
public class ProcExpertService {

    private final ExpertMapper expertMapper;

    /** 抽取请求体 */
    @Data
    public static class ExtractRequest {
        private Long procProjectId;
        private Integer count;
        private String avoidKeyword;
    }

    public PageResult<ProcExpert> page(long current, long size, String specialty, String status) {
        LambdaQueryWrapper<ProcExpert> qw = new LambdaQueryWrapper<>();
        if (specialty != null && !specialty.isBlank()) qw.like(ProcExpert::getSpecialty, specialty);
        if (status != null && !status.isBlank()) qw.eq(ProcExpert::getStatus, status);
        qw.orderByDesc(ProcExpert::getId);
        Page<ProcExpert> p = expertMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<ProcExpert> list(String specialty, String status) {
        LambdaQueryWrapper<ProcExpert> qw = new LambdaQueryWrapper<>();
        if (specialty != null && !specialty.isBlank()) qw.like(ProcExpert::getSpecialty, specialty);
        if (status != null && !status.isBlank()) qw.eq(ProcExpert::getStatus, status);
        qw.orderByAsc(ProcExpert::getId);
        return expertMapper.selectList(qw);
    }

    public ProcExpert detail(Long id) {
        ProcExpert e = expertMapper.selectById(id);
        if (e == null) throw new BizException("专家不存在");
        return e;
    }

    @Transactional
    public Long create(ProcExpert expert) {
        if (expert.getName() == null || expert.getName().isBlank()) throw new BizException("缺少专家姓名");
        expert.setId(null);
        if (expert.getStatus() == null || expert.getStatus().isBlank()) expert.setStatus("ACTIVE");
        expertMapper.insert(expert);
        return expert.getId();
    }

    @Transactional
    public void update(ProcExpert expert) {
        ProcExpert db = detail(expert.getId());
        if (expert.getName() != null) db.setName(expert.getName());
        db.setPhone(expert.getPhone());
        db.setSpecialty(expert.getSpecialty());
        db.setAvoidOrgs(expert.getAvoidOrgs());
        if (expert.getStatus() != null) db.setStatus(expert.getStatus());
        if (expertMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        expertMapper.deleteById(id);
    }

    /** 抽取：ACTIVE 且 avoid_orgs 不含回避关键词的专家中随机取 count 名（不足则返回全部可用） */
    public List<ProcExpert> extract(ExtractRequest req) {
        if (req.getCount() == null || req.getCount() <= 0) throw new BizException("抽取数量需大于0");
        List<ProcExpert> candidates = new ArrayList<>(expertMapper.selectList(
                new LambdaQueryWrapper<ProcExpert>().eq(ProcExpert::getStatus, "ACTIVE")));
        if (req.getAvoidKeyword() != null && !req.getAvoidKeyword().isBlank()) {
            String kw = req.getAvoidKeyword();
            candidates.removeIf(e -> e.getAvoidOrgs() != null && e.getAvoidOrgs().contains(kw));
        }
        Collections.shuffle(candidates);
        return candidates.size() > req.getCount() ? new ArrayList<>(candidates.subList(0, req.getCount())) : candidates;
    }
}
