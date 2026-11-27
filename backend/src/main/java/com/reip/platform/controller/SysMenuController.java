package com.reip.platform.controller;

import com.reip.common.result.Result;
import com.reip.platform.entity.SysMenu;
import com.reip.platform.service.SysMgmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sys/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMgmtService svc;

    @GetMapping("/tree")
    public Result<?> tree() {
        return Result.ok(svc.menuTree());
    }

    @PostMapping
    public Result<Long> save(@RequestBody SysMenu menu) {
        return Result.ok(svc.saveMenu(menu));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.deleteMenu(id);
        return Result.ok();
    }
}
