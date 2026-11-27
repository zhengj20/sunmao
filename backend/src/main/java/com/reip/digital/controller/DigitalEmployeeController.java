package com.reip.digital.controller;

import com.reip.common.result.Result;
import com.reip.digital.entity.DigEmployee;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.service.DigitalEmployeeService;
import com.reip.digital.service.DigitalExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数字员工（角色）：看板 / CRUD / 技能装配 / 手动执行
 */
@RestController
@RequestMapping("/api/digital/employee")
@RequiredArgsConstructor
public class DigitalEmployeeController {

    private final DigitalEmployeeService svc;
    private final DigitalExecutor executor;

    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board() {
        return Result.ok(svc.board());
    }

    @GetMapping("/list")
    public Result<List<DigEmployee>> list() {
        return Result.ok(svc.list());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        DigEmployee e = svc.detail(id);
        List<DigSkill> skills = svc.skillsOf(id);
        return Result.ok(Map.of("employee", e, "skills", skills));
    }

    @PostMapping
    public Result<Long> create(@RequestBody EmployeePayload p) {
        return Result.ok(svc.create(p.employee(), p.skillIds()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody EmployeePayload p) {
        svc.update(id, p.employee(), p.skillIds());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/skills")
    public Result<Void> setSkills(@PathVariable Long id, @RequestBody SkillsPayload p) {
        svc.setSkills(id, p.skillIds());
        return Result.ok();
    }

    /** 快捷切换在岗/休假（休假期间定时与条件任务自动跳过） */
    @PostMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody StatusPayload p) {
        svc.toggleStatus(id, p.status());
        return Result.ok();
    }

    @GetMapping("/{id}/skills")
    public Result<List<DigSkill>> skills(@PathVariable Long id) {
        return Result.ok(svc.skillsOf(id));
    }

    /** 手动执行任务（异步，返回执行记录ID供轮询过程） */
    @PostMapping("/{id}/run")
    public Result<Map<String, Object>> run(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object taskId = body.get("taskId");
        if (taskId == null) return Result.fail("请选择要执行的任务");
        Long runId = executor.execute(Long.valueOf(taskId.toString()), "MANUAL");
        return Result.ok(Map.of("runId", runId));
    }

    /** 请求体：员工字段 + 技能ID列表（null=不变更技能） */
    public record EmployeePayload(DigEmployee employee, List<Long> skillIds) {}

    public record SkillsPayload(List<Long> skillIds) {}

    public record StatusPayload(String status) {}
}
