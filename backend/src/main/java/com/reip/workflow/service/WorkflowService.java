package com.reip.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.LoginUser;
import com.reip.framework.auth.UserContext;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.workflow.entity.WflCc;
import com.reip.workflow.entity.WflEntrust;
import com.reip.workflow.entity.WflInstance;
import com.reip.workflow.entity.WflTask;
import com.reip.workflow.entity.WflTemplate;
import com.reip.workflow.flowable.ApproverResolver;
import com.reip.workflow.flowable.FlowableTemplateDeployer;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流引擎（Flowable 底座）：任务动作（审批/驳回/转办）+ 待办已办查询。
 * 或签/会签/条件路由由 BPMN 承载（userTask / multiInstance / exclusiveGateway）；
 * wfl_instance 为业务实例记录，wfl_task 为在途台账（供门户/超时/统计跨模块读取），
 * 流程正常结束（走到底）发布 workflow.instance.finished(approved=true)，驳回/撤回 approved=false。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowMappers.TemplateMapper templateMapper;
    private final WorkflowMappers.InstanceMapper instanceMapper;
    private final WorkflowMappers.TaskMapper taskMapper;
    private final WorkflowMappers.EntrustMapper entrustMapper;
    private final WorkflowMappers.CcMapper ccMapper;
    private final WorkflowMappers.UserLookupMapper userLookupMapper;
    private final FlowableTemplateDeployer deployer;
    private final ApproverResolver approverResolver;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ParamService paramService;

    // ================= 发起 =================

    /** 发起审批：写业务实例 → 确保部署 → 启动 Flowable 流程实例（process key=bizType） */
    @Transactional
    public void startFlow(String bizType, Long bizId, String title, BigDecimal amount, Map<String, Object> snapshot) {
        WflTemplate tpl = templateMapper.selectOne(new LambdaQueryWrapper<WflTemplate>()
                .eq(WflTemplate::getBizType, bizType).eq(WflTemplate::getStatus, "ACTIVE"));
        if (tpl == null) throw new BizException("未配置审批流模板:" + bizType);

        Long inflight = instanceMapper.selectCount(new LambdaQueryWrapper<WflInstance>()
                .eq(WflInstance::getBizType, bizType)
                .eq(WflInstance::getBizId, bizId)
                .eq(WflInstance::getInflightFlag, 1));
        if (inflight > 0) throw new BizException("该单据已有在途审批流程");

        LoginUser me = UserContext.require();
        WflInstance inst = new WflInstance();
        inst.setTemplateId(tpl.getId());
        inst.setTemplateCode(tpl.getTemplateCode());
        inst.setBizType(bizType);
        inst.setBizId(bizId);
        inst.setBizTitle(title);
        inst.setBizAmount(amount);
        inst.setSnapshot(toJson(snapshot));
        inst.setInflightFlag(1);
        inst.setStatus("RUNNING");
        inst.setSubmitterId(me.getUserId());
        inst.setSubmitterName(me.getRealName());
        inst.setSubmittedAt(LocalDateTime.now());
        instanceMapper.insert(inst);

        deployer.ensureDeployed(tpl);

        Map<String, Object> vars = new HashMap<>();
        vars.put("wflInstanceId", inst.getId());
        vars.put("bizType", bizType);
        vars.put("bizId", bizId);
        vars.put("bizTitle", title);
        vars.put("bizAmount", amount == null ? BigDecimal.ZERO : amount);
        vars.put("amount", amount == null ? BigDecimal.ZERO : amount); // CONDITION 路由用
        vars.put("submitterId", me.getUserId());
        vars.put("submitterName", me.getRealName());
        vars.put("submittedAt", inst.getSubmittedAt() == null ? null : inst.getSubmittedAt().toString());
        // 每个 APPROVE 节点：解析审批人（含委托代理）→ approvers_ 供候选/多实例；wflBase_ 供台账；wflCs_ 供展示
        JsonNode def;
        try {
            def = objectMapper.readTree(tpl.getDefinition());
        } catch (Exception e) {
            throw new BizException("模板[" + tpl.getTemplateCode() + "]定义JSON解析失败");
        }
        for (JsonNode n : def.path("nodes")) {
            if (!"APPROVE".equals(n.path("type").asText("APPROVE"))) continue;
            String nodeId = n.path("id").asText();
            List<Long> base = approverResolver.baseUsers(n, bizType);
            if (base.isEmpty()) throw new BizException("审批节点[" + n.path("name").asText(nodeId) + "]未找到可用审批人");
            vars.put("wflBase_" + nodeId, base);
            vars.put("wflCs_" + nodeId, n.path("countersign").asText("ANY"));
        }

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(bizType, vars);
        attachCandidates(pi.getProcessInstanceId(), vars, bizType);
        WflInstance up = new WflInstance();
        up.setId(inst.getId());
        up.setVersion(inst.getVersion());
        up.setInflightFlag(1); // inflightFlag 为 ALWAYS 更新策略：部分更新须显式赋值，避免被置 NULL
        up.setProcessInstanceId(pi.getProcessInstanceId());
        instanceMapper.updateById(up);
        inst.setProcessInstanceId(pi.getProcessInstanceId());
        syncLedger(pi.getProcessInstanceId(), inst);
    }

    /** 撤回：仅发起人可撤，结束流程实例并发布 finished(approved=false) */
    @Transactional
    public void withdrawFlow(String bizType, Long bizId) {
        WflInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<WflInstance>()
                .eq(WflInstance::getBizType, bizType)
                .eq(WflInstance::getBizId, bizId)
                .eq(WflInstance::getInflightFlag, 1));
        if (inst == null) throw new BizException("未找到在途审批流程");
        if (!"RUNNING".equals(inst.getStatus())) throw new BizException("流程不在运行中，无法撤回");
        LoginUser me = UserContext.require();
        if (!me.getUserId().equals(inst.getSubmitterId())) throw new BizException("仅发起人可撤回流程");

        if (inst.getProcessInstanceId() != null) {
            runtimeService.deleteProcessInstance(inst.getProcessInstanceId(), "发起人撤回");
        }
        finalizeInstance(inst.getId(), "WITHDRAWN");
        cancelLedgerPENDING(inst.getId());
    }

    /** 当前用户待办任务数 */
    public long todoCount() {
        Long me = UserContext.currentUserId();
        if (me == null) return 0;
        String meStr = String.valueOf(me);
        return taskService.createTaskQuery().active()
                .taskCandidateUser(meStr).or().taskAssignee(meStr).endOr().count();
    }

    // ================= 任务动作 =================

    /** 审批通过：或签一人通过即推进；会签全部通过才推进（Flowable 天然满足）。走到底则发布 finished(approved=true) */
    public void approve(String taskId, String comment) {
        Task task = requireTask(taskId);
        checkActor(task);
        WflInstance inst = instanceByTask(task);
        String marked = agentMark(task, comment);
        String pi = task.getProcessInstanceId();

        taskService.complete(task.getId());
        log.info("[workflow] approve complete OK, taskId={}", taskId);

        // 流程推进后为新任务挂候选人（TaskListener expression 不可靠时的兜底）
        try {
            Map<String, Object> vars = runtimeService.getVariables(pi);
            attachCandidates(pi, vars, inst.getBizType());
        } catch (Exception e) {
            log.debug("[workflow] 流程已结束，无需挂候选人: {}", e.getMessage());
        }

        autoCcSubmitter(inst, task.getName(), "通过", marked);
        if (isEnded(pi)) {
            finalizeInstance(inst.getId(), "APPROVED");
        }
        syncLedger(pi, inst);
    }

    /** 驳回：整单驳回，结束实例，发布 finished(approved=false) */
    @Transactional
    public void reject(String taskId, String comment) {
        if (comment == null || comment.isBlank()) throw new BizException("请填写驳回原因");
        Task task = requireTask(taskId);
        checkActor(task);
        WflInstance inst = instanceByTask(task);
        String me = String.valueOf(UserContext.require().getUserId());
        String marked = agentMark(task, comment);

        autoCcSubmitter(inst, task.getName(), "驳回", marked);
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), "驳回:" + marked);
        finalizeInstance(inst.getId(), "REJECTED");
        cancelLedgerPENDING(inst.getId());
    }

    /** 转办：把任务指派给目标人（原办理人释放） */
    @Transactional
    public void transfer(String taskId, Long toUserId, String toUserName, String comment) {
        if (toUserId == null) throw new BizException("请选择转办目标人");
        Task task = requireTask(taskId);
        checkActor(task);
        WflInstance inst = instanceByTask(task);
        String to = String.valueOf(toUserId);
        taskService.setAssignee(task.getId(), to);
        if (comment != null && !comment.isBlank()) {
            taskService.setVariableLocal(task.getId(), "wflComment", "转办给" + (toUserName == null ? to : toUserName) + "：" + comment);
        }
        syncLedger(task.getProcessInstanceId(), inst);
    }

    /**
     * 加签：before=true 前加签（当前任务追加候选审批人）；
     * before=false 后加签（Flowable 不支持动态插入节点，给出明确提示，不造假）。
     */
    @Transactional
    public void addSign(String taskId, List<Long> userIds, boolean before) {
        if (userIds == null || userIds.isEmpty()) throw new BizException("请选择加签人");
        if (!before) throw new BizException("后加签（动态插入节点）在当前 Flowable 引擎下不支持，请使用转办或前加签");
        Task task = requireTask(taskId);
        checkActor(task);
        WflInstance inst = instanceByTask(task);
        for (Long uid : userIds.stream().distinct().collect(Collectors.toList())) {
            addCandidateIfAbsent(task, uid);
        }
        syncLedger(task.getProcessInstanceId(), inst);
    }

    /** 抄送：为任务创建抄送记录（可多人） */
    @Transactional
    public void cc(String taskId, List<Long> userIds, String content) {
        if (userIds == null || userIds.isEmpty()) throw new BizException("请选择抄送人");
        Task task = requireTask(taskId);
        WflInstance inst = instanceByTask(task);
        List<Long> ids = userIds.stream().distinct().collect(Collectors.toList());
        for (Long uid : ids) {
            insertCc(inst.getId(), toWflTaskId(task), uid, nameOf(uid), content);
        }
    }

    /** 我收到的抄送（当前用户，分页） */
    public PageResult<WflCc> myCcPage(long current, long size) {
        Long me = UserContext.require().getUserId();
        Page<WflCc> p = ccMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<WflCc>()
                .eq(WflCc::getCcUserId, me)
                .orderByDesc(WflCc::getCreatedAt).orderByDesc(WflCc::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 批量审批：逐个 approve，返回成功/失败明细（单个失败不影响其余） */
    public List<Map<String, Object>> batchApprove(List<String> taskIds, String comment) {
        if (taskIds == null || taskIds.isEmpty()) throw new BizException("请选择要审批的任务");
        List<String> ids = taskIds.stream().distinct().collect(Collectors.toList());
        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        for (String tid : ids) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("taskId", tid);
            try {
                approve(tid, comment);
                r.put("success", true);
            } catch (Exception e) {
                r.put("success", false);
                r.put("msg", e.getMessage());
            }
            results.add(r);
        }
        return results;
    }

    // ================= 查询 =================

    /** 当前用户待办（候选人或办理人） */
    public PageResult<TaskView> todoPage(long current, long size) {
        String me = String.valueOf(UserContext.require().getUserId());
        var q = taskService.createTaskQuery()
                .taskCandidateUser(me)
                .orderByTaskCreateTime().asc();
        long total = q.count();
        log.info("[workflow] todoPage me={} total={} (no or-clause)", me, total);
        List<Task> tasks = q.listPage((int) ((current - 1) * size), (int) size);
        List<TaskView> views = tasks.stream().map(t -> toTaskView(t, nameOf(UserContext.currentUserId()))).collect(Collectors.toList());
        return PageResult.of(views, total, current, size);
    }

    /** 当前用户已办（我审批/驳回的） */
    public PageResult<TaskView> donePage(long current, long size) {
        String me = String.valueOf(UserContext.require().getUserId());
        var q = historyService.createHistoricTaskInstanceQuery()
                .or().taskCandidateUser(me).taskAssignee(me).endOr()
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc();
        long total = q.count();
        List<HistoricTaskInstance> tasks = q.listPage((int) ((current - 1) * size), (int) size);
        List<TaskView> views = tasks.stream().map(this::toTaskViewDone).collect(Collectors.toList());
        return PageResult.of(views, total, current, size);
    }

    /** 我发起的流程 */
    public PageResult<WflInstance> minePage(long current, long size) {
        Long me = UserContext.require().getUserId();
        Page<WflInstance> p = instanceMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<WflInstance>()
                .eq(WflInstance::getSubmitterId, me)
                .orderByDesc(WflInstance::getSubmittedAt).orderByDesc(WflInstance::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 实例详情 + 全部任务（流程追踪），每个任务附带其节点 fieldPerm */
    public Map<String, Object> instanceDetail(Long id) {
        WflInstance inst = instanceMapper.selectById(id);
        if (inst == null) throw new BizException("流程实例不存在");

        List<HistoricTaskInstance> hist = new ArrayList<>();
        if (inst.getProcessInstanceId() != null) {
            hist = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(inst.getProcessInstanceId())
                    .orderByTaskCreateTime().asc().list();
        }

        Map<String, List<Map<String, Object>>> permByNode = nodeFieldPerm(inst);
        List<WflTask> tasks = new ArrayList<>(hist.size());
        for (HistoricTaskInstance h : hist) {
            WflTask t = toWflTask(h);
            List<Map<String, Object>> fp = permByNode.get(t.getNodeId());
            t.setFieldPerm(fp == null ? Collections.emptyList() : fp);
            tasks.add(t);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", inst);
        result.put("tasks", tasks);
        return result;
    }

    /** 流程监控列表（含停留时长分钟） */
    public PageResult<InstanceView> instancePage(long current, long size, String status, String bizType) {
        LambdaQueryWrapper<WflInstance> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(WflInstance::getStatus, status);
        if (bizType != null && !bizType.isBlank()) qw.eq(WflInstance::getBizType, bizType);
        qw.orderByDesc(WflInstance::getSubmittedAt).orderByDesc(WflInstance::getId);
        Page<WflInstance> p = instanceMapper.selectPage(new Page<>(current, size), qw);
        LocalDateTime now = LocalDateTime.now();
        List<InstanceView> views = p.getRecords().stream().map(i -> {
            InstanceView v = new InstanceView();
            v.setId(i.getId());
            v.setBizType(i.getBizType());
            v.setBizId(i.getBizId());
            v.setBizTitle(i.getBizTitle());
            v.setBizAmount(i.getBizAmount());
            v.setStatus(i.getStatus());
            v.setCurrentNodeId(i.getCurrentNodeId());
            v.setSubmitterName(i.getSubmitterName());
            v.setSubmittedAt(i.getSubmittedAt());
            v.setFinishedAt(i.getFinishedAt());
            v.setStayMinutes(i.getSubmittedAt() == null ? null
                    : Duration.between(i.getSubmittedAt(), i.getFinishedAt() != null ? i.getFinishedAt() : now).toMinutes());
            return v;
        }).collect(Collectors.toList());
        return PageResult.of(views, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 统计：在途数/超时数/本月完成数/平均审批时长分钟（wfl_task 台账由本服务维护） */
    public Map<String, Object> stats() {
        int hours = paramService.getInt("wfl.approve.timeout.hours", 48);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return instanceMapper.selectStats(LocalDateTime.now().minusHours(hours), monthStart);
    }

    // ================= 私有工具：Flowable 映射 =================

    private Task requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) throw new BizException("缺少任务ID");
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (t == null) throw new BizException("任务不存在或已处理，请刷新待办列表");
        return t;
    }

    /** 当前用户须为该任务的候选人或办理人（或签候选人/会签字任务候选人/转办后的办理人） */
    private void checkActor(Task task) {
        Long me = UserContext.require().getUserId();
        String meStr = String.valueOf(me);
        boolean ok = meStr.equals(task.getAssignee()) || isCandidate(task, meStr);
        if (!ok) throw new BizException("无权处理该审批任务");
    }

    private WflInstance instanceByTask(Task task) {
        if (task.getProcessInstanceId() != null) {
            WflInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<WflInstance>()
                    .eq(WflInstance::getProcessInstanceId, task.getProcessInstanceId())
                    .last("LIMIT 1"));
            if (inst != null) return inst;
        }
        throw new BizException("流程实例不存在");
    }

    private boolean isEnded(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult() == null;
    }

    private void attachCandidates(String processInstanceId, Map<String, Object> vars, String bizType) {
        List<Task> active;
        try {
            active = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        } catch (Exception e) {
            log.warn("[workflow] 查询活动任务失败: {}", e.getMessage());
            return;
        }
        for (Task t : active) {
            String nodeKey = t.getTaskDefinitionKey();
            // ALL 会签字任务：approver 局部变量 = 当前子任务对应审批人
            Object approver = null;
            try { approver = taskService.getVariableLocal(t.getId(), "approver"); } catch (Exception ignored) {}
            if (approver != null) {
                Long uid = toLong(approver);
                if (uid != null) {
                    taskService.addCandidateUser(t.getId(), String.valueOf(uid));
                    WflEntrust ent = agentOf(uid, bizType);
                    if (ent != null && ent.getToUserId() != null) {
                        taskService.addCandidateUser(t.getId(), String.valueOf(ent.getToUserId()));
                    }
                }
            } else {
                Object base = vars.get("wflBase_" + nodeKey);
                if (!(base instanceof List<?> bl)) continue;
                for (Object o : bl) {
                    Long uid = toLong(o);
                    if (uid == null) continue;
                    taskService.addCandidateUser(t.getId(), String.valueOf(uid));
                    WflEntrust ent = agentOf(uid, bizType);
                    if (ent != null && ent.getToUserId() != null) {
                        taskService.addCandidateUser(t.getId(), String.valueOf(ent.getToUserId()));
                    }
                }
            }
        }
    }

    private void addCandidateIfAbsent(Task t, Long uid) {
        if (uid == null) return;
        String u = String.valueOf(uid);
        if (!isCandidate(t, u)) taskService.addCandidateUser(t.getId(), u);
    }

    /** 当前用户是否为该任务候选人（通过 taskService 服务 API 查询，避免直接调用实体懒加载方法） */
    private boolean isCandidate(Task t, String userId) {
        if (userId == null) return false;
        List<org.flowable.identitylink.api.IdentityLink> links = taskService.getIdentityLinksForTask(t.getId());
        if (links == null) return false;
        for (org.flowable.identitylink.api.IdentityLink link : links) {
            if ("candidate".equals(link.getType()) && userId.equals(link.getUserId())) return true;
        }
        return false;
    }

    /** 结束业务实例并发布完成事件（业务模块据此回写单据状态）；幂等（仅 RUNNING→终态才发布） */
    private void finalizeInstance(Long wflInstanceId, String status) {
        WflInstance inst = instanceMapper.selectById(wflInstanceId);
        if (inst == null) throw new BizException("流程实例不存在");
        if (!"RUNNING".equals(inst.getStatus())) return;
        WflInstance up = new WflInstance();
        up.setId(inst.getId());
        up.setVersion(inst.getVersion());
        up.setStatus(status);
        up.setInflightFlag(null);
        up.setFinishedAt(LocalDateTime.now());
        if (instanceMapper.updateById(up) == 0) return;
        eventPublisher.publish("workflow.instance.finished", inst.getBizType(), inst.getBizId(),
                Map.of("bizType", inst.getBizType(), "bizId", inst.getBizId(),
                        "approved", "APPROVED".equals(status), "instanceId", inst.getId()));
    }

    /** 审批通过/驳回时自动抄送发起人（同 task 已抄送则跳过，避免重复） */
    private void autoCcSubmitter(WflInstance inst, String nodeName, String action, String comment) {
        Long submitter = inst.getSubmitterId();
        if (submitter == null) return;
        Long taskId = toWflTaskIdOfInstance(inst);
        Long exists = ccMapper.selectCount(new LambdaQueryWrapper<WflCc>()
                .eq(WflCc::getTaskId, taskId).eq(WflCc::getCcUserId, submitter));
        if (exists != null && exists > 0) return;
        String title = inst.getBizTitle() == null ? inst.getBizType() : inst.getBizTitle();
        String content = "您提交的【" + title + "】在「" + nodeName + "」被" + action
                + (comment == null || comment.isBlank() ? "" : "，意见：" + comment);
        insertCc(inst.getId(), taskId, submitter, inst.getSubmitterName(), content);
    }

    private Long toWflTaskIdOfInstance(WflInstance inst) {
        // 取该实例台账中最近一条任务ID作为抄送来源（无则用实例ID占位）
        WflTask t = taskMapper.selectOne(new LambdaQueryWrapper<WflTask>()
                .eq(WflTask::getInstanceId, inst.getId()).orderByDesc(WflTask::getId).last("LIMIT 1"));
        return t == null ? inst.getId() : t.getId();
    }

    private Long toWflTaskId(Task task) {
        return toWflTaskIdOfInstance(instanceByTask(task));
    }

    private void insertCc(Long instanceId, Long taskId, Long userId, String userName, String content) {
        WflCc c = new WflCc();
        c.setInstanceId(instanceId);
        c.setTaskId(taskId);
        c.setCcUserId(userId);
        c.setCcUserName(userName);
        c.setContent(content);
        ccMapper.insert(c);
    }

    /** 代理人审批留痕：意见前追加"代审批"（当前人是候选人但非基础审批人时） */
    private String agentMark(Task task, String comment) {
        Long me = UserContext.currentUserId();
        if (me == null) return comment;
        String meStr = String.valueOf(me);
        if (isCandidate(task, meStr)) {
            Object base = taskVar(task, "wflBase_" + task.getTaskDefinitionKey());
            if (base instanceof List<?> list && !list.contains(me)) {
                return "代审批" + (comment == null || comment.isBlank() ? "" : "：" + comment);
            }
        }
        return comment;
    }

    // ================= 私有工具：wfl_task 在途台账（门户/超时/统计跨模块读取） =================

    private void syncLedger(String processInstanceId, WflInstance inst) {
        List<Task> active;
        try {
            active = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        } catch (Exception e) {
            active = new ArrayList<>();
        }
        String bizType = inst.getBizType();
        Set<String> activeKeys = new HashSet<>();
        for (Task t : active) {
            String nodeKey = t.getTaskDefinitionKey();
            boolean all = taskVar(t, "loopCounter") != null;
            if (all) {
                Object approver = taskVar(t, "approver");
                if (approver != null) {
                    Long uid = toLong(approver);
                    activeKeys.add(nodeKey + "#" + uid);
                    upsertLedger(inst, nodeKey, t.getName(), uid, "ALL", bizType);
                }
            } else {
                Object base = taskVar(t, "wflBase_" + nodeKey);
                if (!(base instanceof List<?> bl)) continue;
                for (Object o : bl) {
                    Long uid = toLong(o);
                    activeKeys.add(nodeKey + "#" + uid);
                    upsertLedger(inst, nodeKey, t.getName(), uid, "ANY", bizType);
                }
            }
        }
        // 作废不再在途的 PENDING 台账
        List<WflTask> pendings = taskMapper.selectList(new LambdaQueryWrapper<WflTask>()
                .eq(WflTask::getInstanceId, inst.getId()).eq(WflTask::getStatus, "PENDING"));
        for (WflTask p : pendings) {
            if (!activeKeys.contains(p.getNodeId() + "#" + p.getAssigneeId())) {
                WflTask up = new WflTask();
                up.setId(p.getId());
                up.setVersion(p.getVersion());
                up.setStatus("CANCELLED");
                taskMapper.updateById(up);
            }
        }
    }

    private void upsertLedger(WflInstance inst, String nodeKey, String nodeName, Long uid, String cs, String bizType) {
        WflTask existing = taskMapper.selectOne(new LambdaQueryWrapper<WflTask>()
                .eq(WflTask::getInstanceId, inst.getId())
                .eq(WflTask::getNodeId, nodeKey)
                .eq(WflTask::getAssigneeId, uid)
                .eq(WflTask::getStatus, "PENDING")
                .last("LIMIT 1"));
        WflEntrust entrust = agentOf(uid, bizType);
        WflTask t = new WflTask();
        t.setInstanceId(inst.getId());
        t.setNodeId(nodeKey);
        t.setNodeName(nodeName);
        t.setAssigneeId(uid);
        t.setAssigneeName(nameOf(uid));
        t.setAgentId(entrust == null ? null : entrust.getToUserId());
        t.setAgentName(entrust == null ? null : entrust.getToUserName());
        t.setCountersign(cs);
        t.setStatus("PENDING");
        if (existing != null) {
            t.setId(existing.getId());
            t.setVersion(existing.getVersion());
            taskMapper.updateById(t);
        } else {
            t.setArrivedAt(LocalDateTime.now());
            taskMapper.insert(t);
        }
    }

    private void cancelLedgerPENDING(Long instanceId) {
        List<WflTask> pendings = taskMapper.selectList(new LambdaQueryWrapper<WflTask>()
                .eq(WflTask::getInstanceId, instanceId).eq(WflTask::getStatus, "PENDING"));
        for (WflTask p : pendings) {
            WflTask up = new WflTask();
            up.setId(p.getId());
            up.setVersion(p.getVersion());
            up.setStatus("CANCELLED");
            taskMapper.updateById(up);
        }
    }

    // ================= 私有工具：DTO 映射 =================

    private TaskView toTaskView(Task t, String myName) {
        TaskView v = new TaskView();
        v.setTaskId(t.getId());
        v.setInstanceId(toLong(taskVar(t, "wflInstanceId")));
        v.setNodeId(t.getTaskDefinitionKey());
        v.setNodeName(t.getName());
        v.setStatus("PENDING");
        v.setCountersign(csOf(t));
        v.setAssigneeId(toLong(t.getAssignee() == null ? meVar(t) : t.getAssignee()));
        v.setAssigneeName(myName);
        v.setComment(null);
        v.setArrivedAt(t.getCreateTime() == null ? null : t.getCreateTime().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        v.setBizType(str(taskVar(t, "bizType")));
        v.setBizId(toLong(taskVar(t, "bizId")));
        v.setBizTitle(str(taskVar(t, "bizTitle")));
        Object amt = taskVar(t, "bizAmount");
        v.setBizAmount(amt == null ? null : new BigDecimal(amt.toString()));
        v.setSubmitterName(str(taskVar(t, "submitterName")));
        Object subAt = taskVar(t, "submittedAt");
        v.setSubmittedAt(subAt instanceof LocalDateTime ldt ? ldt : (subAt == null ? null : LocalDateTime.parse(subAt.toString())));
        return v;
    }

    private TaskView toTaskViewDone(HistoricTaskInstance h) {
        TaskView v = new TaskView();
        v.setTaskId(h.getId());
        v.setInstanceId(toLong(histVar(h, "wflInstanceId")));
        v.setNodeId(h.getTaskDefinitionKey());
        v.setNodeName(h.getName());
        v.setStatus("APPROVED");
        v.setCountersign(csOf(h));
        Long actor = toLong(h.getAssignee());
        v.setAssigneeId(actor);
        v.setAssigneeName(actor == null ? null : nameOf(actor));
        Object comment = histVar(h, "wflComment");
        v.setComment(comment == null ? null : comment.toString());
        v.setArrivedAt(h.getCreateTime() == null ? null : h.getCreateTime().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        v.setActedAt(h.getEndTime() == null ? null : h.getEndTime().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        if (h.getCreateTime() != null && h.getEndTime() != null) {
            v.setDurationMinutes(Duration.between(v.getArrivedAt(), v.getActedAt()).toMinutes());
        }
        v.setBizType(str(histVar(h, "bizType")));
        v.setBizId(toLong(histVar(h, "bizId")));
        v.setBizTitle(str(histVar(h, "bizTitle")));
        Object amt = histVar(h, "bizAmount");
        v.setBizAmount(amt == null ? null : new BigDecimal(amt.toString()));
        v.setSubmitterName(str(histVar(h, "submitterName")));
        Object subAt = histVar(h, "submittedAt");
        v.setSubmittedAt(subAt instanceof LocalDateTime ldt ? ldt : (subAt == null ? null : LocalDateTime.parse(subAt.toString())));
        return v;
    }

    private WflTask toWflTask(HistoricTaskInstance h) {
        WflTask t = new WflTask();
        t.setFlowableTaskId(h.getId());
        try {
            t.setId(Long.parseLong(h.getId()));
        } catch (NumberFormatException e) {
            t.setId(null);
        }
        t.setInstanceId(toLong(histVar(h, "wflInstanceId")));
        t.setNodeId(h.getTaskDefinitionKey());
        t.setNodeName(h.getName());
        Long actor = toLong(h.getAssignee());
        t.setAssigneeId(actor);
        t.setAssigneeName(actor == null ? null : nameOf(actor));
        Object comment = histVar(h, "wflComment");
        t.setComment(comment == null ? null : comment.toString());
        t.setCountersign(csOf(h));
        t.setStatus(h.getEndTime() == null ? "PENDING" : "APPROVED");
        t.setArrivedAt(h.getCreateTime() == null ? null : h.getCreateTime().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        t.setActedAt(h.getEndTime() == null ? null : h.getEndTime().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        if (h.getCreateTime() != null && h.getEndTime() != null) {
            t.setDurationMinutes(Duration.between(t.getArrivedAt(), t.getActedAt()).toMinutes());
        }
        return t;
    }

    private String csOf(Task t) {
        Object cs = taskVar(t, "wflCs_" + t.getTaskDefinitionKey());
        return cs == null ? "ANY" : cs.toString();
    }

    private String csOf(HistoricTaskInstance h) {
        Object cs = histVar(h, "wflCs_" + h.getTaskDefinitionKey());
        return cs == null ? "ANY" : cs.toString();
    }

    private Object meVar(Task t) {
        return taskVar(t, "wflActor");
    }

    /** 解析模板定义，返回 nodeId → fieldPerm 列表（仅含配置了 fieldPerm 的节点） */
    private Map<String, List<Map<String, Object>>> nodeFieldPerm(WflInstance inst) {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();
        WflTemplate tpl = templateMapper.selectById(inst.getTemplateId());
        if (tpl == null || tpl.getDefinition() == null) return m;
        try {
            JsonNode def = objectMapper.readTree(tpl.getDefinition());
            for (JsonNode n : def.path("nodes")) {
                JsonNode fp = n.path("fieldPerm");
                if (fp.isArray() && fp.size() > 0) m.put(n.path("id").asText(), toMapList(fp));
            }
        } catch (Exception ignored) {
            // 定义解析失败不影响详情展示
        }
        return m;
    }

    private List<Map<String, Object>> toMapList(JsonNode arr) {
        try {
            return objectMapper.convertValue(arr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private WflEntrust agentOf(Long userId, String bizType) {
        return entrustMapper.selectOne(new LambdaQueryWrapper<WflEntrust>()
                .eq(WflEntrust::getFromUserId, userId)
                .eq(WflEntrust::getStatus, "ACTIVE")
                .le(WflEntrust::getStartDate, LocalDate.now())
                .ge(WflEntrust::getEndDate, LocalDate.now())
                .and(w -> w.eq(WflEntrust::getBizScope, "ALL").or().eq(WflEntrust::getBizScope, bizType))
                .orderByDesc(WflEntrust::getId)
                .last("LIMIT 1"));
    }

    private String nameOf(Long uid) {
        if (uid == null) return null;
        List<Map<String, Object>> us = userLookupMapper.selectUsersByIds(List.of(uid));
        if (us.isEmpty()) return String.valueOf(uid);
        Object name = us.get(0).get("real_name");
        return name == null ? String.valueOf(uid) : name.toString();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 安全读取活动任务变量（流程变量优先，再任务局部变量） */
    private Object taskVar(Task t, String name) {
        try {
            return taskService.getVariable(t.getId(), name);
        } catch (Exception e) {
            // 非流程变量，继续尝试任务局部变量
        }
        try {
            return taskService.getVariableLocal(t.getId(), name);
        } catch (Exception e) {
            return null;
        }
    }

    /** 安全读取历史任务变量（按 taskId 查历史变量实例） */
    private Object histVar(HistoricTaskInstance h, String name) {
        try {
            org.flowable.variable.api.history.HistoricVariableInstance v = historyService
                    .createHistoricVariableInstanceQuery().taskId(h.getId()).variableName(name).singleResult();
            return v == null ? null : v.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException("单据快照序列化失败");
        }
    }

    // ================= 视图 DTO（前端契约，保持不变） =================

    /** 待办/已办视图：任务 + 实例摘要 */
    @Data
    public static class TaskView {
        private String taskId;
        private Long instanceId;
        private String nodeId;
        private String nodeName;
        private String status;
        private String countersign;
        private Long assigneeId;
        private String assigneeName;
        private Long agentId;
        private String agentName;
        private String comment;
        private LocalDateTime arrivedAt;
        private LocalDateTime actedAt;
        private Long durationMinutes;
        private String bizType;
        private Long bizId;
        private String bizTitle;
        private BigDecimal bizAmount;
        private String submitterName;
        private LocalDateTime submittedAt;
    }

    /** 流程监控视图：实例 + 停留时长 */
    @Data
    public static class InstanceView {
        private Long id;
        private String bizType;
        private Long bizId;
        private String bizTitle;
        private BigDecimal bizAmount;
        private String status;
        private String currentNodeId;
        private String submitterName;
        private LocalDateTime submittedAt;
        private LocalDateTime finishedAt;
        /** 停留时长分钟 = 现在或finishedAt - submittedAt */
        private Long stayMinutes;
    }
}
