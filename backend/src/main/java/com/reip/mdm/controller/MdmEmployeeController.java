package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmEmployee;
import com.reip.mdm.service.MdmEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 员工档案
 */
@RestController
@RequestMapping("/api/mdm/employees")
@RequiredArgsConstructor
public class MdmEmployeeController {

    private final MdmEmployeeService svc;

    /** 员工分页（关联登录账号/组织展示） */
    @GetMapping("/page")
    public Result<PageResult<MdmEmployee>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String kw,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long orgId) {
        return Result.ok(svc.page(current, size, kw, status, orgId));
    }

    @GetMapping("/{id}")
    public Result<MdmEmployee> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmEmployee employee) {
        return Result.ok(svc.create(employee));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmEmployee employee) {
        svc.update(employee);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
