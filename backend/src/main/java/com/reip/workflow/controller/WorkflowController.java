package com.reip.workflow.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.workflow.api.WorkflowClient;
import com.reip.workflow.entity.WflCc;
import com.reip.workflow.entity.WflInstance;
import com.reip.workflow.service.WorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流：待办/已办/我发起的/流程追踪/流程监控 + 任务动作
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowClient workflowClient;

    // ================= 查询 =================

    @GetMapping("/todos/page")
    public Result<PageResult<WorkflowService.TaskView>> todos(@RequestParam(defaultValue = "1") long current,
                                                               @RequestParam(defaultValue = "20") long size) {
        return Result.ok(workflowService.todoPage(current, size));
    }

    @GetMapping("/dones/page")
    public Result<PageResult<WorkflowService.TaskView>> dones(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "20") long size) {
        return Result.ok(workflowService.donePage(current, size));
    }

    @GetMapping("/mine/page")
    public Result<PageResult<WflInstance>> mine(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size) {
        return Result.ok(workflowService.minePage(current, size));
    }

    @GetMapping("/instances/page")
    public Result<PageResult<WorkflowService.InstanceView>> instances(@RequestParam(defaultValue = "1") long current,
                                                                      @RequestParam(defaultValue = "20") long size,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String bizType) {
        return Result.ok(workflowService.instancePage(current, size, status, bizType));
    }

    @GetMapping("/instances/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(workflowService.stats());
    }

    @GetMapping("/instances/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(workflowService.instanceDetail(id));
    }

    // ================= 任务动作 =================

    @PostMapping("/tasks/{taskId}/approve")
    public Result<Void> approve(@PathVariable String taskId,
                                @RequestBody(required = false) ActionForm form) {
        workflowService.approve(taskId, form == null ? null : form.getComment());
        return Result.ok();
    }

    @PostMapping("/tasks/{taskId}/reject")
    public Result<Void> reject(@PathVariable String taskId,
                               @RequestBody(required = false) ActionForm form) {
        workflowService.reject(taskId, form == null ? null : form.getComment());
        return Result.ok();
    }

    @PostMapping("/tasks/{taskId}/transfer")
    public Result<Void> transfer(@PathVariable String taskId, @RequestBody TransferForm form) {
        workflowService.transfer(taskId, form.getToUserId(), form.getToUserName(), form.getComment());
        return Result.ok();
    }

    /** 加签：before=true 前加签(同节点会签)；before=false 后加签(插入新节点) */
    @PostMapping("/tasks/{taskId}/add-sign")
    public Result<Void> addSign(@PathVariable String taskId, @RequestBody SignForm form) {
        workflowService.addSign(taskId, form.getUserIds(), form.isBefore());
        return Result.ok();
    }

    /** 抄送：为任务创建抄送（可多人） */
    @PostMapping("/tasks/{taskId}/cc")
    public Result<Void> cc(@PathVariable String taskId, @RequestBody CcForm form) {
        workflowService.cc(taskId, form.getUserIds(), form.getContent());
        return Result.ok();
    }

    /** 我收到的抄送（分页） */
    @GetMapping("/ccs/my")
    public Result<PageResult<WflCc>> myCc(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size) {
        return Result.ok(workflowService.myCcPage(current, size));
    }

    /** 批量审批：逐个校验并审批，返回成功/失败明细 */
    @PostMapping("/tasks/batch-approve")
    public Result<List<Map<String, Object>>> batchApprove(@RequestBody BatchForm form) {
        return Result.ok(workflowService.batchApprove(form.getTaskIds(), form.getComment()));
    }

    /** 发起人撤回在途流程 */
    @PostMapping("/withdraw")
    public Result<Void> withdraw(@RequestBody WithdrawForm form) {
        workflowClient.withdraw(form.getBizType(), form.getBizId());
        return Result.ok();
    }

    @Data
    public static class ActionForm {
        private String comment;
    }

    @Data
    public static class TransferForm {
        private Long toUserId;
        private String toUserName;
        private String comment;
    }

    @Data
    public static class WithdrawForm {
        private String bizType;
        private Long bizId;
    }

    @Data
    public static class SignForm {
        private List<Long> userIds;
        private boolean before;
    }

    @Data
    public static class CcForm {
        private List<Long> userIds;
        private String content;
    }

    @Data
    public static class BatchForm {
        private List<String> taskIds;
        private String comment;
    }
}
