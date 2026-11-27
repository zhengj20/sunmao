package com.reip.workflow.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.workflow.entity.WflTemplate;
import com.reip.workflow.service.WflTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流程模板管理
 */
@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WflTemplateController {

    private final WflTemplateService svc;

    @GetMapping("/page")
    public Result<PageResult<WflTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, kw));
    }

    /** 读取模板定义（含 definition JSON / fieldPerm） */
    @GetMapping("/{id}")
    public Result<WflTemplate> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新增/编辑模板（definition 为 JSON 字符串） */
    @PostMapping
    public Result<Long> save(@RequestBody WflTemplate template) {
        return Result.ok(svc.save(template));
    }

    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        svc.archive(id);
        return Result.ok();
    }
}
