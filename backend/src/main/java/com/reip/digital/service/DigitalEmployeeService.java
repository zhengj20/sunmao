package com.reip.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.ai.entity.AiModelConfig;
import com.reip.ai.mapper.AiMappers;
import com.reip.common.exception.BizException;
import com.reip.digital.entity.DigEmployee;
import com.reip.digital.entity.DigEmployeeSkill;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.entity.DigTask;
import com.reip.digital.entity.DigTaskRun;
import com.reip.digital.entity.DigWorkflow;
import com.reip.digital.entity.DigWorkflowRun;
import com.reip.digital.entity.DigWorkflowStep;
import com.reip.digital.mapper.DigitalMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数字员工（角色）管理：增删改查 + 技能装配 + 团队看板（含任务/最近运行/绑定模型）
 */
@Service
@RequiredArgsConstructor
public class DigitalEmployeeService {

    private final DigitalMappers.EmployeeMapper mapper;
    private final DigitalMappers.EmployeeSkillMapper esMapper;
    private final DigitalMappers.SkillMapper skillMapper;
    private final DigitalMappers.TaskMapper taskMapper;
    private final DigitalMappers.TaskRunMapper runMapper;
    private final DigitalMappers.WorkflowMapper wfMapper;
    private final DigitalMappers.WorkflowStepMapper wfStepMapper;
    private final DigitalMappers.WorkflowRunMapper wfRunMapper;
    private final AiMappers.ModelConfigMapper modelMapper;

    public List<DigEmployee> list() {
        return mapper.selectList(new LambdaQueryWrapper<DigEmployee>()
                .orderByAsc(DigEmployee::getId));
    }

    public DigEmployee detail(Long id) {
        return require(id);
    }

    /** 团队看板：员工 + 技能 + 任务 + 绑定模型 + 最近运行 + 是否在运行 + 参与的工作流 */
    public List<Map<String, Object>> board() {
        List<Map<String, Object>> out = new ArrayList<>();
        // 预加载工作流及其环节（一次查询，避免 N+1）
        List<DigWorkflow> wfs = wfMapper.selectList(new LambdaQueryWrapper<DigWorkflow>()
                .eq(DigWorkflow::getStatus, "ACTIVE").orderByAsc(DigWorkflow::getId));
        List<DigWorkflowStep> allWfSteps = wfStepMapper.selectList(new LambdaQueryWrapper<DigWorkflowStep>()
                .orderByAsc(DigWorkflowStep::getWfId).orderByAsc(DigWorkflowStep::getStepOrder));
        // 任务 → 员工 的映射
        Map<Long, Long> taskOwnerMap = new LinkedHashMap<>();
        if (!allWfSteps.isEmpty()) {
            List<Long> wfTaskIds = allWfSteps.stream().map(DigWorkflowStep::getTaskId)
                    .filter(java.util.Objects::nonNull).distinct().toList();
            if (!wfTaskIds.isEmpty()) {
                for (DigTask t : taskMapper.selectBatchIds(wfTaskIds)) {
                    if (t.getEmployeeId() != null) taskOwnerMap.put(t.getId(), t.getEmployeeId());
                }
            }
        }
        // 每个员工的参与情况：员工ID → [{wfId,wfName,stepOrder,stepName}]
        Map<Long, List<Map<String, Object>>> wfByEmp = new LinkedHashMap<>();
        for (DigWorkflowStep st : allWfSteps) {
            Long empId = st.getTaskId() == null ? null : taskOwnerMap.get(st.getTaskId());
            if (empId == null) continue;
            DigWorkflow wf = wfs.stream().filter(w -> w.getId().equals(st.getWfId())).findFirst().orElse(null);
            if (wf == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wfId", wf.getId());
            item.put("wfName", wf.getName());
            item.put("stepOrder", st.getStepOrder());
            item.put("stepName", st.getName() == null || st.getName().isBlank() ? ("第" + st.getStepOrder() + "步") : st.getName());
            item.put("requiresApproval", st.getRequiresApproval() != null && st.getRequiresApproval() == 1);
            wfByEmp.computeIfAbsent(empId, k -> new ArrayList<>()).add(item);
        }
        // 每个工作流最近一次运行状态
        Map<Long, DigWorkflowRun> latestWfRun = new LinkedHashMap<>();
        for (DigWorkflowRun r : wfRunMapper.selectList(new LambdaQueryWrapper<DigWorkflowRun>()
                .orderByDesc(DigWorkflowRun::getId).last("LIMIT 50"))) {
            latestWfRun.putIfAbsent(r.getWfId(), r);
        }

        for (DigEmployee e : list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employee", e);
            row.put("skills", skillsOf(e.getId()));
            row.put("tasks", tasksOf(e.getId()));
            row.put("model", modelOf(e.getModelConfigId()));
            DigTaskRun last = runMapper.selectOne(new LambdaQueryWrapper<DigTaskRun>()
                    .eq(DigTaskRun::getEmployeeId, e.getId())
                    .orderByDesc(DigTaskRun::getId).last("LIMIT 1"));
            row.put("lastRun", last);
            Long running = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>()
                    .eq(DigTaskRun::getEmployeeId, e.getId()).eq(DigTaskRun::getStatus, "RUNNING"));
            row.put("running", running != null && running > 0);
            Long done = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>()
                    .eq(DigTaskRun::getEmployeeId, e.getId()).eq(DigTaskRun::getStatus, "DONE"));
            Long failed = runMapper.selectCount(new LambdaQueryWrapper<DigTaskRun>()
                    .eq(DigTaskRun::getEmployeeId, e.getId()).eq(DigTaskRun::getStatus, "FAILED"));
            long totalRuns = (done == null ? 0 : done) + (failed == null ? 0 : failed);
            row.put("totalRuns", totalRuns);
            row.put("successRate", totalRuns == 0 ? null
                    : Math.round((double) (done == null ? 0 : done) * 100.0 / totalRuns));
            // 该员工参与的工作流（含最近运行状态），供卡片展示
            List<Map<String, Object>> wfPart = wfByEmp.getOrDefault(e.getId(), List.of());
            for (Map<String, Object> item : wfPart) {
                DigWorkflowRun lr = latestWfRun.get((Long) item.get("wfId"));
                item.put("lastStatus", lr == null ? null : lr.getStatus());
            }
            row.put("workflows", wfPart);
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Long create(DigEmployee e, List<Long> skillIds) {
        if (e == null) throw new BizException("缺少员工信息");
        if (e.getName() == null || e.getName().isBlank()) throw new BizException("请填写数字员工名称");
        if (e.getRoleCode() == null || e.getRoleCode().isBlank()) e.setRoleCode("CUSTOM");
        if (e.getAvatar() == null || e.getAvatar().isBlank()) e.setAvatar("custom");
        if (e.getStatus() == null) e.setStatus("ACTIVE");
        if (e.getModelConfigId() != null) {
            AiModelConfig m = modelMapper.selectById(e.getModelConfigId());
            if (m == null) throw new BizException("绑定的模型配置不存在");
        }
        e.setId(null);
        mapper.insert(e);
        if (skillIds != null && !skillIds.isEmpty()) {
            for (Long sid : skillIds) link(e.getId(), sid);
        }
        return e.getId();
    }

    @Transactional
    public void update(Long id, DigEmployee e, List<Long> skillIds) {
        DigEmployee cur = require(id);
        if (e.getName() != null) cur.setName(e.getName());
        if (e.getRoleCode() != null) cur.setRoleCode(e.getRoleCode());
        if (e.getAvatar() != null) cur.setAvatar(e.getAvatar());
        if (e.getPersona() != null) cur.setPersona(e.getPersona());
        if (e.getStatus() != null) cur.setStatus(e.getStatus());
        if (e.getRemark() != null) cur.setRemark(e.getRemark());
        if (e.getModelConfigId() != null) {
            AiModelConfig m = modelMapper.selectById(e.getModelConfigId());
            if (m == null) throw new BizException("绑定的模型配置不存在");
            cur.setModelConfigId(e.getModelConfigId());
        }
        // 知识库绑定：非 null 时全量覆盖（空串=解绑全部）
        if (e.getKbDocIds() != null) cur.setKbDocIds(e.getKbDocIds().isBlank() ? null : e.getKbDocIds().trim());
        if (mapper.updateById(cur) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        // skillIds 非 null 时视为全量重装配（null=不变更）
        if (skillIds != null) {
            esMapper.delete(new LambdaQueryWrapper<DigEmployeeSkill>().eq(DigEmployeeSkill::getEmployeeId, id));
            for (Long sid : skillIds) link(id, sid);
        }
    }

    @Transactional
    public void delete(Long id) {
        DigEmployee e = require(id);
        // 删除其技能关联与任务定义；执行记录保留留痕
        esMapper.delete(new LambdaQueryWrapper<DigEmployeeSkill>().eq(DigEmployeeSkill::getEmployeeId, id));
        taskMapper.delete(new LambdaQueryWrapper<DigTask>().eq(DigTask::getEmployeeId, id));
        mapper.deleteById(id);
    }

    /** 快捷切换在岗/休假（休假期间定时与条件任务自动跳过） */
    public void toggleStatus(Long id, String status) {
        DigEmployee e = require(id);
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BizException("状态非法：" + status);
        }
        e.setStatus(status);
        if (mapper.updateById(e) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 全量重装配技能 */
    @Transactional
    public void setSkills(Long id, List<Long> skillIds) {
        require(id);
        esMapper.delete(new LambdaQueryWrapper<DigEmployeeSkill>().eq(DigEmployeeSkill::getEmployeeId, id));
        if (skillIds != null) {
            for (Long sid : skillIds) link(id, sid);
        }
    }

    public List<DigSkill> skillsOf(Long employeeId) {
        List<DigEmployeeSkill> links = esMapper.selectList(new LambdaQueryWrapper<DigEmployeeSkill>()
                .eq(DigEmployeeSkill::getEmployeeId, employeeId).orderByAsc(DigEmployeeSkill::getSort));
        if (links.isEmpty()) return List.of();
        List<Long> ids = links.stream().map(DigEmployeeSkill::getSkillId).toList();
        List<DigSkill> all = skillMapper.selectBatchIds(ids);
        List<DigSkill> out = new ArrayList<>();
        for (Long sid : ids) {
            all.stream().filter(s -> s.getId().equals(sid)).findFirst().ifPresent(out::add);
        }
        return out;
    }

    private void link(Long employeeId, Long skillId) {
        if (skillId == null) return;
        Long exists = esMapper.selectCount(new LambdaQueryWrapper<DigEmployeeSkill>()
                .eq(DigEmployeeSkill::getEmployeeId, employeeId).eq(DigEmployeeSkill::getSkillId, skillId));
        if (exists != null && exists > 0) return;
        DigEmployeeSkill es = new DigEmployeeSkill();
        es.setEmployeeId(employeeId);
        es.setSkillId(skillId);
        es.setSort(0);
        esMapper.insert(es);
    }

    private List<DigTask> tasksOf(Long employeeId) {
        return taskMapper.selectList(new LambdaQueryWrapper<DigTask>()
                .eq(DigTask::getEmployeeId, employeeId).orderByAsc(DigTask::getId));
    }

    private Map<String, Object> modelOf(Long modelConfigId) {
        if (modelConfigId == null) {
            AiModelConfig def = modelMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getStatus, "ACTIVE").orderByAsc(AiModelConfig::getId).last("LIMIT 1"));
            if (def == null) return Map.of("default", true, "label", "未配置模型（规则引擎模式）");
            return Map.of("default", true, "id", def.getId(), "provider", def.getProvider(),
                    "modelName", def.getModelName() == null ? "" : def.getModelName());
        }
        AiModelConfig m = modelMapper.selectById(modelConfigId);
        if (m == null) return Map.of("default", false, "label", "模型配置已删除（运行时回退默认）");
        return Map.of("default", false, "id", m.getId(), "provider", m.getProvider(),
                "modelName", m.getModelName() == null ? "" : m.getModelName(),
                "active", "ACTIVE".equals(m.getStatus()));
    }

    private DigEmployee require(Long id) {
        if (id == null) throw new BizException("缺少员工ID");
        DigEmployee e = mapper.selectById(id);
        if (e == null) throw new BizException("数字员工不存在");
        return e;
    }
}
