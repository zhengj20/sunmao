package com.reip.platform.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.platform.entity.SysUser;
import com.reip.platform.service.SysMgmtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysMgmtService svc;

    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String kw,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(svc.userPage(current, size, kw, status));
    }

    @GetMapping("/{id}/roles")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(svc.userRoleIds(id));
    }

    @Data
    public static class UserForm {
        private SysUser user;
        private List<Long> roleIds;
    }

    @PostMapping
    public Result<Long> create(@RequestBody UserForm form) {
        return Result.ok(svc.createUser(form.getUser(), form.getRoleIds()));
    }

    @PutMapping
    public Result<Void> update(@RequestBody UserForm form) {
        svc.updateUser(form.getUser(), form.getRoleIds());
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        svc.resetPassword(id, body.get("password"));
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        svc.toggleUserStatus(id, status);
        return Result.ok();
    }
}
