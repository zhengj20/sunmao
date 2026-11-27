package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmCodeRule;
import com.reip.mdm.service.MdmCodeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 编码规则
 */
@RestController
@RequestMapping("/api/mdm/coderules")
@RequiredArgsConstructor
public class MdmCodeRuleController {

    private final MdmCodeRuleService svc;

    /** 规则分页 */
    @GetMapping("/page")
    public Result<PageResult<MdmCodeRule>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, kw));
    }

    /** 预览：解析 pattern 展示示例编码（seq=当前值+1，不消耗计数器） */
    @GetMapping("/preview/{ruleCode}")
    public Result<Map<String, Object>> preview(@PathVariable String ruleCode) {
        return Result.ok(svc.preview(ruleCode));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmCodeRule rule) {
        return Result.ok(svc.create(rule));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmCodeRule rule) {
        svc.update(rule);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
