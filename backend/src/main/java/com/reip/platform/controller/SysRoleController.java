package com.reip.platform.controller;

import com.reip.common.result.Result;
import com.reip.platform.entity.SysRole;
import com.reip.platform.service.SysMgmtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysMgmtService svc;

    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.ok(svc.roleList());
    }

    @GetMapping("/{id}/menus")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.ok(svc.roleMenuIds(id));
    }

    @Data
    public static class RoleForm {
        private SysRole role;
        private List<Long> menuIds;
    }

    @PostMapping
    public Result<Long> save(@RequestBody RoleForm form) {
        return Result.ok(svc.saveRole(form.getRole(), form.getMenuIds()));
    }
}
