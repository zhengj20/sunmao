package com.reip.platform.controller;

import com.reip.common.result.Result;
import com.reip.platform.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginReq req) {
        return Result.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    @GetMapping("/menus")
    public Result<?> myMenus() {
        return Result.ok(authService.myMenus());
    }

    @GetMapping("/perms")
    public Result<List<String>> myPerms() {
        return Result.ok(authService.myPerms());
    }
}
