package com.reip.platform.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.platform.entity.SysNotice;
import com.reip.platform.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统公告 /api/sys/notices
 */
@RestController
@RequestMapping("/api/sys/notices")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService svc;

    @GetMapping("/page")
    public Result<PageResult<SysNotice>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String noticeType) {
        return Result.ok(svc.page(current, size, status, noticeType));
    }

    @GetMapping("/{id}")
    public Result<SysNotice> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SysNotice notice) {
        return Result.ok(svc.create(notice));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysNotice notice) {
        notice.setId(id);
        svc.update(notice);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/pin")
    public Result<Void> togglePin(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        svc.togglePin(id, Boolean.TRUE.equals(body.get("pinned")));
        return Result.ok();
    }
}
