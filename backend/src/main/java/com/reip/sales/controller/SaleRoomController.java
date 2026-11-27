package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.framework.auth.UserContext;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.service.SaleRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源销控：列表 / 初始化 / 锁定放开 / 详情
 */
@RestController
@RequestMapping("/api/sales/rooms")
@RequiredArgsConstructor
public class SaleRoomController {

    private final SaleRoomService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleRoomStatus>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) Long buildingId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String kw,
                                                    @RequestParam(required = false) BigDecimal minArea,
                                                    @RequestParam(required = false) BigDecimal maxArea) {
        return Result.ok(svc.page(current, size, projectId, buildingId, status, kw, minArea, maxArea));
    }

    @GetMapping("/{roomId}")
    public Result<SaleRoomStatus> detail(@PathVariable Long roomId) {
        return Result.ok(svc.detail(roomId));
    }

    /** 从 MDM 房间初始化销控（幂等） */
    @PostMapping("/init")
    public Result<Integer> init(@RequestParam Long projectId) {
        return Result.ok(svc.initFromMdm(projectId));
    }

    /** 锁定（当前用户，默认24小时到期） */
    @PostMapping("/{roomId}/lock")
    public Result<Void> lock(@PathVariable Long roomId, @RequestBody(required = false) LockForm form) {
        Long lockBy = UserContext.currentUserId();
        LocalDateTime expire = (form != null && form.getExpire() != null)
                ? form.getExpire() : LocalDateTime.now().plusHours(24);
        svc.lock(roomId, lockBy, expire);
        return Result.ok();
    }

    /** 放开 */
    @PostMapping("/{roomId}/unlock")
    public Result<Void> unlock(@PathVariable Long roomId) {
        svc.unlock(roomId);
        return Result.ok();
    }

    @Data
    public static class LockForm {
        private LocalDateTime expire;
    }
}
