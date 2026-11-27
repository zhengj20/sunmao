package com.reip.workflow.flowable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.workflow.entity.WflEntrust;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务创建时挂接审批候选人（在 Flowable 命令上下文中运行，变量读写可靠）：
 *  - ANY 任务：候选 = 该节点基础审批人（wflBase_<node>）+ 各自的在途委托代理；
 *  - ALL 子任务（multiInstance）：候选 = 该子任务对应的审批人（elementVariable=approver）+ 其代理。
 * 通过 BPMN 中 {@code <flowable:taskListener event="create" class="${wflApproverListener}"/>} 引用本 Spring Bean。
 */
@Component("wflApproverListener")
@RequiredArgsConstructor
public class WflApproverListener implements TaskListener {

    private final WorkflowMappers.EntrustMapper entrustMapper;

    @Override
    public void notify(DelegateTask task) {
        String node = task.getTaskDefinitionKey();
        String bizType = str(task.getVariable("bizType"));
        List<String> candidates = new ArrayList<>();

        Object approver = task.getVariable("approver"); // ALL 子任务的 elementVariable
        if (approver != null) {
            addSelfAndAgent(candidates, toLong(approver), bizType);
        } else {
            Object base = task.getVariable("wflBase_" + node);
            if (base instanceof List<?> list) {
                for (Object o : list) {
                    addSelfAndAgent(candidates, toLong(o), bizType);
                }
            }
        }
        if (!candidates.isEmpty()) {
            task.addCandidateUsers(candidates);
        }
    }

    private void addSelfAndAgent(List<String> candidates, Long uid, String bizType) {
        if (uid == null) return;
        candidates.add(String.valueOf(uid));
        WflEntrust ent = findActiveEntrust(uid, bizType);
        if (ent != null && ent.getToUserId() != null && !ent.getToUserId().equals(uid)) {
            candidates.add(String.valueOf(ent.getToUserId()));
        }
    }

    private WflEntrust findActiveEntrust(Long userId, String bizType) {
        if (userId == null) return null;
        LambdaQueryWrapper<WflEntrust> qw = new LambdaQueryWrapper<WflEntrust>()
                .eq(WflEntrust::getFromUserId, userId)
                .eq(WflEntrust::getStatus, "ACTIVE")
                .le(WflEntrust::getStartDate, LocalDate.now())
                .ge(WflEntrust::getEndDate, LocalDate.now());
        if (bizType != null) {
            qw.and(w -> w.eq(WflEntrust::getBizScope, "ALL").or().eq(WflEntrust::getBizScope, bizType));
        }
        qw.orderByDesc(WflEntrust::getId).last("LIMIT 1");
        return entrustMapper.selectOne(qw);
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
}
