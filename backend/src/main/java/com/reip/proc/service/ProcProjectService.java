package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 招采立项（注意与业务项目 mdm_project 区分：本表是招采立项单）
 * 阳光采购：方式矩阵建议 + 直接委托理由必填
 */
@Service
@RequiredArgsConstructor
public class ProcProjectService {

    /** 六种采购方式（与 mdm_dict 种子 procurement.method 一致） */
    private static final Set<String> PROCURE_METHODS =
            Set.of("PUBLIC_BID", "INVITE_BID", "COMPETITIVE", "INQUIRY", "DIRECT", "FRAMEWORK");

    private final ProjectMapper projectMapper;
    private final CodeRuleService codeRuleService;
    private final ParamService paramService;
    private final WorkflowClient workflowClient;

    public PageResult<ProcProject> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<ProcProject> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(ProcProject::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(ProcProject::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(ProcProject::getProjectCode, kw)
                .or().like(ProcProject::getTitle, kw));
        qw.orderByDesc(ProcProject::getId);
        Page<ProcProject> p = projectMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcProject detail(Long id) {
        ProcProject p = projectMapper.selectById(id);
        if (p == null) throw new BizException("招采立项不存在");
        return p;
    }

    @Transactional
    public Long create(ProcProject project) {
        validateMethod(project.getProcureMethod(), project.getMethodReason());
        project.setId(null);
        project.setProjectCode(codeRuleService.next("CGXM", null));
        project.setStatus("DRAFT");
        projectMapper.insert(project);
        return project.getId();
    }

    @Transactional
    public void update(ProcProject project) {
        ProcProject db = detail(project.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的立项可修改");
        if (project.getProjectId() != null) db.setProjectId(project.getProjectId());
        if (project.getTitle() != null) db.setTitle(project.getTitle());
        db.setBudgetAmount(project.getBudgetAmount());
        db.setPlanItemId(project.getPlanItemId());
        db.setFundSource(project.getFundSource());
        if (project.getProcureMethod() != null) db.setProcureMethod(project.getProcureMethod());
        db.setMethodReason(project.getMethodReason());
        db.setPeriodDays(project.getPeriodDays());
        db.setRemark(project.getRemark());
        validateMethod(db.getProcureMethod(), db.getMethodReason());
        if (projectMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcProject db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的立项可删除");
        projectMapper.deleteById(id);
    }

    /** 提交审批（PROC_PROJECT，金额=预算）：DRAFT→APPROVING */
    @Transactional
    public void submit(Long id) {
        ProcProject db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的立项可提交审批");
        db.setStatus("APPROVING");
        if (projectMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("立项编号", db.getProjectCode());
        snapshot.put("标的名称", db.getTitle());
        snapshot.put("采购方式", db.getProcureMethod());
        snapshot.put("预算金额", db.getBudgetAmount());
        snapshot.put("工期(天)", db.getPeriodDays());
        workflowClient.start("PROC_PROJECT", id, "招采立项审批：" + db.getTitle(),
                db.getBudgetAmount(), snapshot);
    }

    /**
     * 采购方式矩阵建议：≥公开招标门槛（proc.public.bid.threshold，默认500万）建议公开招标
     */
    public Map<String, Object> suggestMethod(BigDecimal amount) {
        if (amount == null) throw new BizException("请传入金额");
        int threshold = paramService.getInt("proc.public.bid.threshold", 5_000_000);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        m.put("threshold", BigDecimal.valueOf(threshold));
        String amtText = amount.stripTrailingZeros().toPlainString();
        if (amount.compareTo(BigDecimal.valueOf(threshold)) >= 0) {
            m.put("suggestedMethod", "PUBLIC_BID");
            m.put("suggestedMethodName", "公开招标");
            m.put("reason", "金额" + amtText + "元已达公开招标门槛" + threshold + "元，依据阳光采购规则建议采用公开招标");
        } else {
            m.put("suggestedMethod", "COMPETITIVE");
            m.put("suggestedMethodName", "竞争性谈判");
            m.put("reason", "金额" + amtText + "元未达公开招标门槛" + threshold + "元，建议采用竞争性谈判或询比价方式");
        }
        return m;
    }

    /** 方式合法性 + 直接委托理由必填（阳光采购红线） */
    private void validateMethod(String procureMethod, String methodReason) {
        if (procureMethod != null && !PROCURE_METHODS.contains(procureMethod)) {
            throw new BizException("无效的采购方式：" + procureMethod);
        }
        if ("DIRECT".equals(procureMethod) && (methodReason == null || methodReason.isBlank())) {
            throw new BizException("直接委托必须填写方式选择理由");
        }
    }
}
