package com.reip.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.framework.param.ParamService;
import com.reip.workflow.entity.WflTask;
import com.reip.workflow.entity.WflTimeoutLog;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批超时巡检：PENDING 任务超过阈值小时数记录 wfl_timeout_log 并告警（同任务只记一次）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTimeoutChecker {

    private final WorkflowMappers.TaskMapper taskMapper;
    private final WorkflowMappers.TimeoutLogMapper timeoutLogMapper;
    private final ParamService paramService;

    @Scheduled(fixedDelay = 3600000) // 每小时
    public void check() {
        int hours = paramService.getInt("wfl.approve.timeout.hours", 48);
        LocalDateTime deadline = LocalDateTime.now().minusHours(hours);
        List<WflTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<WflTask>()
                .eq(WflTask::getStatus, "PENDING")
                .lt(WflTask::getArrivedAt, deadline));
        for (WflTask t : tasks) {
            Long logged = timeoutLogMapper.selectCount(new LambdaQueryWrapper<WflTimeoutLog>()
                    .eq(WflTimeoutLog::getTaskId, t.getId()));
            if (logged > 0) continue; // 幂等：同任务只记一次
            WflTimeoutLog entry = new WflTimeoutLog();
            entry.setTaskId(t.getId());
            entry.setInstanceId(t.getInstanceId());
            entry.setNodeName(t.getNodeName());
            entry.setAssigneeId(t.getAssigneeId());
            entry.setTimeoutHours(hours);
            entry.setActionTaken("REMIND");
            entry.setCreatedAt(LocalDateTime.now());
            timeoutLogMapper.insert(entry);
            log.warn("[workflow] 审批超时提醒 taskId={} instanceId={} node={} assigneeId={} 已停留超过{}小时",
                    t.getId(), t.getInstanceId(), t.getNodeName(), t.getAssigneeId(), hours);
        }
    }
}
