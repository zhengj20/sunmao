package com.reip.workflow.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.workflow.entity.WflEntrust;
import com.reip.workflow.service.WflEntrustService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委托代理管理
 */
@RestController
@RequestMapping("/api/workflow/entrusts")
@RequiredArgsConstructor
public class WflEntrustController {

    private final WflEntrustService svc;

    /** 分页列表（全部） */
    @GetMapping("/page")
    public Result<PageResult<WflEntrust>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, status));
    }

    /** 我的有效委托 */
    @GetMapping("/mine")
    public Result<List<WflEntrust>> mine() {
        return Result.ok(svc.mine());
    }

    /** 创建委托（from_user 为当前人） */
    @PostMapping
    public Result<Long> create(@RequestBody WflEntrust entrust) {
        return Result.ok(svc.create(entrust));
    }

    /** 结束委托 */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        svc.close(id);
        return Result.ok();
    }
}
