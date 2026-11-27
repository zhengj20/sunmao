package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.proc.entity.ProcRequirement;
import com.reip.proc.mapper.ProcMappers.RequirementMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采购需求：需求→计划→立项 全流程起点
 */
@Service
@RequiredArgsConstructor
public class ProcRequirementService {

    private final RequirementMapper requirementMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;

    public PageResult<ProcRequirement> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<ProcRequirement> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(ProcRequirement::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(ProcRequirement::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(ProcRequirement::getReqCode, kw)
                .or().like(ProcRequirement::getTitle, kw));
        qw.orderByDesc(ProcRequirement::getId);
        Page<ProcRequirement> p = requirementMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcRequirement detail(Long id) {
        ProcRequirement r = requirementMapper.selectById(id);
        if (r == null) throw new BizException("采购需求不存在");
        return r;
    }

    @Transactional
    public Long create(ProcRequirement req) {
        req.setId(null);
        req.setReqCode(codeRuleService.next("CGXQ", null));
        req.setStatus("DRAFT");
        if (req.getUrgency() == null) req.setUrgency("NORMAL");
        if (req.getCoveredByFramework() == null) req.setCoveredByFramework(0);
        requirementMapper.insert(req);
        return req.getId();
    }

    @Transactional
    public void update(ProcRequirement req) {
        ProcRequirement db = detail(req.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的需求可修改");
        if (req.getProjectId() != null) db.setProjectId(req.getProjectId());
        if (req.getTitle() != null) db.setTitle(req.getTitle());
        if (req.getAccountId() != null) db.setAccountId(req.getAccountId());
        if (req.getCategoryId() != null) db.setCategoryId(req.getCategoryId());
        db.setPlanItemId(req.getPlanItemId());
        db.setDescription(req.getDescription());
        db.setEstimateAmount(req.getEstimateAmount());
        db.setExpectDate(req.getExpectDate());
        if (req.getUrgency() != null) db.setUrgency(req.getUrgency());
        if (req.getCoveredByFramework() != null) db.setCoveredByFramework(req.getCoveredByFramework());
        db.setRemark(req.getRemark());
        if (requirementMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcRequirement db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的需求可删除");
        requirementMapper.deleteById(id);
    }

    /** 提交审批（PROC_REQUIREMENT）：DRAFT→APPROVING */
    @Transactional
    public void submit(Long id) {
        ProcRequirement db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的需求可提交审批");
        db.setStatus("APPROVING");
        if (requirementMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("单据编号", db.getReqCode());
        snapshot.put("需求标题", db.getTitle());
        snapshot.put("预估金额", db.getEstimateAmount());
        snapshot.put("期望时间", db.getExpectDate());
        snapshot.put("紧急程度", "URGENT".equals(db.getUrgency()) ? "紧急" : "普通");
        snapshot.put("集采覆盖", db.getCoveredByFramework() != null && db.getCoveredByFramework() == 1 ? "是" : "否");
        workflowClient.start("PROC_REQUIREMENT", id, "采购需求审批：" + db.getTitle(),
                db.getEstimateAmount(), snapshot);
    }
}
