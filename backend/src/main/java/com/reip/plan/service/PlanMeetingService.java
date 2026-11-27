package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import com.reip.plan.entity.MeetingTask;
import com.reip.plan.entity.PlanMeeting;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 会议管理 + 决议任务闭环（超期提醒）
 */
@Service
@RequiredArgsConstructor
public class PlanMeetingService {

    private final PlanMappers.MeetingMapper meetingMapper;
    private final PlanMappers.MeetingTaskMapper taskMapper;
    private final CodeRuleService codeRuleService;
    private final PlanSupport support;

    // ==================== 查询 ====================

    public PageResult<PlanMeeting> page(long current, long size, Long projectId, String meetingType, String kw) {
        LambdaQueryWrapper<PlanMeeting> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(PlanMeeting::getProjectId, projectId);
        if (meetingType != null && !meetingType.isBlank()) qw.eq(PlanMeeting::getMeetingType, meetingType);
        if (kw != null && !kw.isBlank()) qw.like(PlanMeeting::getMeetingNo, kw);
        qw.orderByDesc(PlanMeeting::getId);
        Page<PlanMeeting> p = meetingMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PlanMeeting detail(Long id) {
        PlanMeeting m = require(id);
        m.setTasks(taskMapper.selectList(new LambdaQueryWrapper<MeetingTask>()
                .eq(MeetingTask::getMeetingId, id).orderByAsc(MeetingTask::getId)));
        return m;
    }

    // ==================== 会议 + 决议任务 ====================

    @Transactional
    public Long create(PlanMeeting m) {
        if (m == null) throw new BizException("缺少会议内容");
        if (m.getProjectId() == null) throw new BizException("请选择项目");
        m.setId(null);
        m.setMeetingNo(codeRuleService.next("HTYH", support.codeCtx(m.getProjectId())));
        if (m.getMeetingType() == null) m.setMeetingType("REGULAR");
        if (m.getStatus() == null) m.setStatus("DONE");
        meetingMapper.insert(m);
        return m.getId();
    }

    /** 添加决议任务 */
    @Transactional
    public Long addTask(Long meetingId, MeetingTask t) {
        PlanMeeting m = require(meetingId);
        if (t == null || t.getTaskDesc() == null || t.getTaskDesc().isBlank()) throw new BizException("请填写决议任务");
        if (t.getRespUserId() == null) t.setRespUserId(UserContext.require().getUserId());
        if (t.getRespUserName() == null) t.setRespUserName(UserContext.require().getRealName());
        t.setMeetingId(m.getId());
        t.setId(null);
        t.setStatus("PENDING");
        taskMapper.insert(t);
        return t.getId();
    }

    /** 决议任务闭环：标记完成 */
    @Transactional
    public void completeTask(Long taskId) {
        MeetingTask t = requireTask(taskId);
        t.setStatus("DONE");
        if (taskMapper.updateById(t) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 超期提醒：将已逾期未完成决议任务标记 OVERDUE，返回清单 */
    @Transactional
    public List<MeetingTask> markOverdue() {
        LocalDate today = LocalDate.now();
        List<MeetingTask> pending = taskMapper.selectList(new LambdaQueryWrapper<MeetingTask>()
                .eq(MeetingTask::getStatus, "PENDING"));
        for (MeetingTask t : pending) {
            if (t.getDueDate() != null && t.getDueDate().isBefore(today)) {
                t.setStatus("OVERDUE");
                taskMapper.updateById(t);
            }
        }
        return taskMapper.selectList(new LambdaQueryWrapper<MeetingTask>().eq(MeetingTask::getStatus, "OVERDUE"));
    }

    // ==================== 私有 ====================

    private PlanMeeting require(Long id) {
        if (id == null) throw new BizException("缺少会议ID");
        PlanMeeting m = meetingMapper.selectById(id);
        if (m == null) throw new BizException("会议不存在");
        return m;
    }

    private MeetingTask requireTask(Long id) {
        if (id == null) throw new BizException("缺少决议任务ID");
        MeetingTask t = taskMapper.selectById(id);
        if (t == null) throw new BizException("决议任务不存在");
        return t;
    }
}
