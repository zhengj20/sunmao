package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleChannel;
import com.reip.sales.service.SaleChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 销售渠道：中介/分销/线上/老带新/自然 —— 渠道主数据 CRUD
 */
@RestController
@RequestMapping("/api/sales/channels")
@RequiredArgsConstructor
public class SaleChannelController {

    private final SaleChannelService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleChannel>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) String channelType,
                                                @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, projectId, channelType, status));
    }

    @GetMapping("/{id}")
    public Result<SaleChannel> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody SaleChannel channel) {
        return Result.ok(svc.create(channel));
    }

    @PutMapping
    public Result<Void> update(@RequestBody SaleChannel channel) {
        svc.update(channel);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
