package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.proc.entity.ProcPlan;
import com.reip.proc.entity.ProcPlanItem;
import com.reip.proc.entity.ProcRequirement;
import com.reip.proc.mapper.ProcMappers.PlanItemMapper;
import com.reip.proc.mapper.ProcMappers.PlanMapper;
import com.reip.proc.mapper.ProcMappers.RequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 采购计划（月度汇总：把已审批需求合并为计划明细）
 */
@Service
@RequiredArgsConstructor
public class ProcPlanService {

    private final PlanMapper planMapper;
    private final PlanItemMapper planItemMapper;
    private final RequirementMapper requirementMapper;
    private final CodeRuleService codeRuleService;

    public PageResult<ProcPlan> page(long current, long size, Long projectId, String status) {
        LambdaQueryWrapper<ProcPlan> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(ProcPlan::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(ProcPlan::getStatus, status);
        qw.orderByDesc(ProcPlan::getId);
        Page<ProcPlan> p = planMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcPlan detail(Long id) {
        ProcPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BizException("采购计划不存在");
        return plan;
    }

    public List<ProcPlanItem> items(Long planId) {
        detail(planId);
        return planItemMapper.selectList(new LambdaQueryWrapper<ProcPlanItem>()
                .eq(ProcPlanItem::getPlanId, planId).orderByAsc(ProcPlanItem::getId));
    }

    @Transactional
    public Long create(ProcPlan plan) {
        plan.setId(null);
        plan.setPlanCode(codeRuleService.next("JH", null));
        if (plan.getPlanMonth() == null || plan.getPlanMonth().isBlank()) {
            plan.setPlanMonth(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        plan.setStatus("DRAFT");
        planMapper.insert(plan);
        return plan.getId();
    }

    @Transactional
    public void update(ProcPlan plan) {
        ProcPlan db = detail(plan.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的计划可修改");
        if (plan.getName() != null) db.setName(plan.getName());
        if (plan.getPlanMonth() != null) db.setPlanMonth(plan.getPlanMonth());
        db.setRemark(plan.getRemark());
        if (planMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcPlan db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的计划可删除");
        // 明细删除，已合并需求回退为已审批（可再并入其他计划）
        List<ProcPlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<ProcPlanItem>()
                .eq(ProcPlanItem::getPlanId, id));
        for (ProcPlanItem item : items) {
            if (item.getRequirementId() != null) {
                ProcRequirement req = requirementMapper.selectById(item.getRequirementId());
                if (req != null && "MERGED".equals(req.getStatus())) {
                    req.setStatus("APPROVED");
                    if (requirementMapper.updateById(req) == 0) {
                        throw new BizException("数据已被他人修改，请刷新后重试");
                    }
                }
            }
            planItemMapper.deleteById(item.getId());
        }
        planMapper.deleteById(id);
    }

    /** 把已审批需求转为计划明细（标题/金额/期望时间带出），需求状态置 MERGED */
    @Transactional
    public Integer mergeRequirements(Long planId, List<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) throw new BizException("请选择要并入的采购需求");
        ProcPlan plan = detail(planId);
        if (!"DRAFT".equals(plan.getStatus())) throw new BizException("仅草稿状态的计划可并入需求");
        int merged = 0;
        for (Long rid : requirementIds.stream().distinct().toList()) {
            ProcRequirement req = requirementMapper.selectById(rid);
            if (req == null) throw new BizException("采购需求不存在：" + rid);
            if (!req.getProjectId().equals(plan.getProjectId())) {
                throw new BizException("需求与计划不属于同一项目：" + req.getReqCode());
            }
            if (!"APPROVED".equals(req.getStatus())) {
                throw new BizException("仅已审批通过的需求可并入计划：" + req.getReqCode());
            }
            ProcPlanItem item = new ProcPlanItem();
            item.setPlanId(planId);
            item.setRequirementId(rid);
            item.setTitle(req.getTitle());
            item.setEstimateAmount(req.getEstimateAmount());
            item.setExpectDate(req.getExpectDate());
            item.setRemark(req.getRemark());
            planItemMapper.insert(item);
            req.setStatus("MERGED");
            if (requirementMapper.updateById(req) == 0) {
                throw new BizException("数据已被他人修改，请刷新后重试");
            }
            merged++;
        }
        return merged;
    }

    /** 计划确认：DRAFT→CONFIRMED */
    @Transactional
    public void confirm(Long id) {
        ProcPlan db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的计划可确认");
        db.setStatus("CONFIRMED");
        if (planMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }
}
