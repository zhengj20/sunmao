package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmDistributeLog;
import com.reip.mdm.service.MdmDistributeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 主数据分发日志
 */
@RestController
@RequestMapping("/api/mdm/distribute-logs")
@RequiredArgsConstructor
public class MdmDistributeLogController {

    private final MdmDistributeLogService svc;

    /** 分发日志分页（可按 dataType 过滤：project/supplier/...） */
    @GetMapping("/page")
    public Result<PageResult<MdmDistributeLog>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) String dataType) {
        return Result.ok(svc.page(current, size, dataType));
    }
}
