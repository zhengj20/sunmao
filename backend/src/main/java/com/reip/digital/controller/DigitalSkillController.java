package com.reip.digital.controller;

import com.reip.common.result.Result;
import com.reip.digital.entity.DigSkill;
import com.reip.digital.service.DigitalSkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 技能市场：内置/市场/自定义技能，全部可编辑配置
 */
@RestController
@RequestMapping("/api/digital/skill")
@RequiredArgsConstructor
public class DigitalSkillController {

    private final DigitalSkillService svc;

    @GetMapping("/list")
    public Result<List<DigSkill>> list(@RequestParam(required = false) String kw,
                                       @RequestParam(required = false) String category) {
        return Result.ok(svc.list(kw, category));
    }

    @GetMapping("/{id}")
    public Result<DigSkill> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody DigSkill s) {
        return Result.ok(svc.create(s));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DigSkill s) {
        svc.update(id, s);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 安装到员工 */
    @PostMapping("/{id}/install")
    public Result<Void> install(@PathVariable Long id, @RequestBody InstallPayload p) {
        svc.installTo(id, p.employeeIds());
        return Result.ok();
    }

    public record InstallPayload(List<Long> employeeIds) {}
}
