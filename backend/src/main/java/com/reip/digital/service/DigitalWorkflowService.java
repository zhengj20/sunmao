package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.*;
import com.reip.digital.mapper.DigitalMappers;
import com.reip.framework.auth.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DigitalWorkflowService {

    private final DigitalMappers.WorkflowMapper wfMapper;
    private final DigitalMappers.WorkflowStepMapper stepMapper;
    private final DigitalMappers.WorkflowRunMapper runMapper;
    private final DigitalMappers.TaskMapper taskMapper;
    private final DigitalMappers.TaskRunMapper taskRunMapper;
    private final DigitalMappers.EmployeeMapper empMapper;
    private final DigitalMappers.SkillMapper skillMapper;
    private final DigitalMappers.WfApprovalMapper approvalMapper;
    private final DigitalWorkflowExecutor wfExecutor;

    /** 工作流列表（含步骤摘要 + 最近运行状态） */
    public List<Map<String, Object>> board() {
        List<DigWorkflow> wfs = wfMapper.selectList(new LambdaQueryWrapper<DigWorkflow>()
                .eq(DigWorkflow::getStatus, "ACTIVE").orderByAsc(DigWorkflow::getId));
        List<DigWorkflowStep> allSteps = stepMapper.selectList(new LambdaQueryWrapper<DigWorkflowStep>()
                .orderByAsc(DigWorkflowStep::getWfId).orderByAsc(DigWorkflowStep::getStepOrder));
        Map<Long, List<DigWorkflowStep>> stepsByWf = allSteps.stream()
                .collect(Collectors.groupingBy(DigWorkflowStep::getWfId));

        // 任务+员工信息
        Set<Long> taskIds = allSteps.stream().map(DigWorkflowStep::getTaskId).collect(Collectors.toSet());
        Map<Long, DigTask> taskMap = new HashMap<>();
        Map<Long, DigEmployee> empMap = new HashMap<>();
        Map<Long, DigSkill> skillMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (DigTask t : taskMapper.selectBatchIds(taskIds)) {
                taskMap.put(t.getId(), t);
                if (t.getEmployeeId() != null) {
                    DigEmployee e = empMapper.selectById(t.getEmployeeId());
                    if (e != null) empMap.put(e.getId(), e);
                }
                if (t.getSkillId() != null) {
                    DigSkill s = skillMapper.selectById(t.getSkillId());
                    if (s != null) skillMap.put(s.getId(), s);
                }
            }
        }

        // 最近运行
        Map<Long, DigWorkflowRun> latestRunByWf = new HashMap<>();
        for (DigWorkflowRun r : runMapper.selectList(new LambdaQueryWrapper<DigWorkflowRun>()
                .orderByDesc(DigWorkflowRun::getId).last("LIMIT 20"))) {
            latestRunByWf.putIfAbsent(r.getWfId(), r);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (DigWorkflow wf : wfs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflow", wf);
            List<Map<String, Object>> steps = new ArrayList<>();
            for (DigWorkflowStep st : stepsByWf.getOrDefault(wf.getId(), List.of())) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("step", st);
                DigTask t = taskMap.get(st.getTaskId());
                if (t != null) {
                    s.put("taskName", t.getName());
                    DigEmployee emp = empMap.get(t.getEmployeeId());
                    s.put("employeeName", emp != null ? emp.getName() : "");
                    DigSkill sk = skillMap.get(t.getSkillId());
                    s.put("skillName", sk != null ? sk.getName() : "");
                    s.put("avatar", emp != null ? emp.getAvatar() : "custom");
                }
                steps.add(s);
            }
            item.put("steps", steps);
            DigWorkflowRun lr = latestRunByWf.get(wf.getId());
            item.put("lastRun", lr);
            result.add(item);
        }
        return result;
    }

    /** 创建工作流 + 步骤 */
    @Transactional
    public DigWorkflow create(DigWorkflow wf, List<DigWorkflowStep> steps) {
        if (wf.getName() == null || wf.getName().isBlank()) throw new BizException("工作流名称不能为空");
        if (wf.getWfCode() == null || wf.getWfCode().isBlank()) {
            wf.setWfCode("WF-" + System.currentTimeMillis());
        }
        applyCron(wf);
        wf.setStatus("ACTIVE");
        wfMapper.insert(wf);
        saveSteps(wf.getId(), steps);
        return wf;
    }

    /** 更新工作流 + 步骤（全量替换步骤） */
    @Transactional
    public void update(Long id, DigWorkflow wf, List<DigWorkflowStep> steps) {
        DigWorkflow existing = wfMapper.selectById(id);
        if (existing == null) throw new BizException("工作流不存在");
        // 以库中值为基准合并，避免未传字段被置空
        if (wf.getName() != null) existing.setName(wf.getName());
        if (wf.getDescription() != null) existing.setDescription(wf.getDescription());
        if (wf.getStatus() != null) existing.setStatus(wf.getStatus());
        if (wf.getCronExpr() != null) existing.setCronExpr(wf.getCronExpr().isBlank() ? null : wf.getCronExpr().trim());
        if (wf.getKbDocIds() != null) existing.setKbDocIds(wf.getKbDocIds().isBlank() ? null : wf.getKbDocIds().trim());
        applyCron(existing);
        wfMapper.updateById(existing);
        // steps==null 表示不变更步骤（避免误删）；非 null 才全量重建
        if (steps != null) {
            // 物理删除旧步骤（唯一键 uk_wf_order 下逻辑删除会残留行导致重建冲突）
            stepMapper.physicalDeleteByWf(id);
            saveSteps(id, steps);
        }
    }

    /** CRON 校验 + 计算下次执行时间（空=仅手动） */
    private void applyCron(DigWorkflow wf) {
        if (wf.getCronExpr() == null || wf.getCronExpr().isBlank()) {
            wf.setCronExpr(null);
            wf.setNextRunAt(null);
            return;
        }
        String cron = wf.getCronExpr().trim();
        try {
            org.springframework.scheduling.support.CronExpression expr = org.springframework.scheduling.support.CronExpression.parse(cron);
            wf.setNextRunAt(expr.next(java.time.LocalDateTime.now()));
        } catch (Exception e) {
            throw new BizException("CRON 表达式无效（需 6 段含秒，如 0 0 8 * * 1-5）");
        }
    }

    /** 删除工作流 */
    @Transactional
    public void delete(Long id) {
        wfMapper.deleteById(id);
        stepMapper.delete(new LambdaQueryWrapper<DigWorkflowStep>().eq(DigWorkflowStep::getWfId, id));
    }

    /** 运行记录分页 */
    public List<DigWorkflowRun> runs(long current, long size, Long wfId) {
        long offset = (current - 1) * size;
        LambdaQueryWrapper<DigWorkflowRun> qw = new LambdaQueryWrapper<DigWorkflowRun>()
                .orderByDesc(DigWorkflowRun::getId).last("LIMIT " + size + " OFFSET " + offset);
        if (wfId != null) qw.eq(DigWorkflowRun::getWfId, wfId);
        return runMapper.selectList(qw);
    }

    public DigWorkflowRun runDetail(Long id) {
        return runMapper.selectById(id);
    }

    /** 待人工审批列表（含工作流/环节/员工信息 + 上游环节产出预览，供人工审核数字员工的工作） */
    public List<Map<String, Object>> pendingApprovals() {
        List<DigWfApproval> list = approvalMapper.selectList(new LambdaQueryWrapper<DigWfApproval>()
                .eq(DigWfApproval::getStatus, "PENDING").orderByAsc(DigWfApproval::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (DigWfApproval a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("approval", a);
            DigWorkflowRun run = runMapper.selectById(a.getWfRunId());
            if (run != null) {
                m.put("wfId", run.getWfId());
                m.put("wfName", run.getWfName());
                m.put("progress", run.getCurrentStep() + "/" + run.getTotalSteps());
                DigWorkflowStep step = stepMapper.selectOne(new LambdaQueryWrapper<DigWorkflowStep>()
                        .eq(DigWorkflowStep::getWfId, run.getWfId())
                        .eq(DigWorkflowStep::getStepOrder, a.getStepOrder()));
                if (step != null && step.getTaskId() != null) {
                    DigTask t = taskMapper.selectById(step.getTaskId());
                    if (t != null) {
                        m.put("taskName", t.getName());
                        if (t.getEmployeeId() != null) {
                            DigEmployee e = empMapper.selectById(t.getEmployeeId());
                            if (e != null) {
                                m.put("employeeName", e.getName());
                                m.put("avatar", e.getAvatar() == null ? "custom" : e.getAvatar());
                            }
                        }
                    }
                }
                // 上游环节产出预览（人工审核数字员工已完成环节的工作）
                m.put("outputPreview", upstreamPreview(run, a.getStepOrder()));
            }
            out.add(m);
        }
        return out;
    }

    /** 取该环节之前最近一个已完成环节的产出（截断 300 字） */
    private String upstreamPreview(DigWorkflowRun run, Integer stepOrder) {
        try {
            List<Map<String, Object>> steps = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(run.getSteps() == null ? "[]" : run.getSteps(), List.class);
            for (int k = stepOrder - 2; k >= 0; k--) {
                if (k >= steps.size()) continue;
                Map<String, Object> s = steps.get(k);
                if (!"DONE".equals(s.get("status"))) continue;
                Object trId = s.get("taskRunId");
                if (trId == null) continue;
                DigTaskRun tr = taskRunMapper.selectById(Long.valueOf(trId.toString()));
                if (tr != null && tr.getOutput() != null && !tr.getOutput().isBlank()) {
                    String o = tr.getOutput().trim();
                    return o.length() > 300 ? o.substring(0, 300) + "…" : o;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /** 审批历史（最近已决策，新→旧） */
    public List<DigWfApproval> approvalHistory(int limit) {
        return approvalMapper.selectList(new LambdaQueryWrapper<DigWfApproval>()
                .ne(DigWfApproval::getStatus, "PENDING")
                .orderByDesc(DigWfApproval::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    /** 审批决策：approve=继续执行该环节；reject=工作流终止 */
    public void decide(Long approvalId, String decision, String comment) {
        boolean approve = "approve".equalsIgnoreCase(decision);
        wfExecutor.decide(approvalId, approve, comment, UserContext.require().getUserId());
    }

    private void saveSteps(Long wfId, List<DigWorkflowStep> steps) {
        if (steps == null) return;
        int order = 1;
        for (DigWorkflowStep st : steps) {
            st.setId(null);
            st.setWfId(wfId);
            st.setStepOrder(order++);
            if (st.getOnFail() == null) st.setOnFail("STOP");
            if (st.getTimeoutSec() == null) st.setTimeoutSec(120);
            if (st.getUsePrev() == null) st.setUsePrev(1);
            if (st.getRequiresApproval() == null) st.setRequiresApproval(0);
            // 步骤名缺省时用绑定任务名兜底，避免运行详情出现空名称
            if ((st.getName() == null || st.getName().isBlank()) && st.getTaskId() != null) {
                DigTask t = taskMapper.selectById(st.getTaskId());
                if (t != null && t.getName() != null) st.setName(t.getName());
            }
            stepMapper.insert(st);
        }
    }
}
