package com.reip.plan.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.plan.entity.MeetingTask;
import com.reip.plan.entity.PlanMeeting;
import com.reip.plan.service.PlanMeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会议管理 + 决议任务闭环（超期提醒）
 */
@RestController
@RequestMapping("/api/plan/meetings")
@RequiredArgsConstructor
public class PlanMeetingController {

    private final PlanMeetingService svc;

    @GetMapping("/page")
    public Result<PageResult<PlanMeeting>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String meetingType,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, meetingType, kw));
    }

    @GetMapping("/{id}")
    public Result<PlanMeeting> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody PlanMeeting m) {
        return Result.ok(svc.create(m));
    }

    /** 添加决议任务 */
    @PostMapping("/{id}/tasks")
    public Result<Long> addTask(@PathVariable Long id, @RequestBody MeetingTask t) {
        return Result.ok(svc.addTask(id, t));
    }

    /** 决议任务闭环：标记完成 */
    @PostMapping("/tasks/{taskId}/done")
    public Result<Void> completeTask(@PathVariable Long taskId) {
        svc.completeTask(taskId);
        return Result.ok();
    }

    /** 超期提醒：标记并返回超期决议任务清单 */
    @GetMapping("/overdue")
    public Result<List<MeetingTask>> overdue() {
        return Result.ok(svc.markOverdue());
    }
}
