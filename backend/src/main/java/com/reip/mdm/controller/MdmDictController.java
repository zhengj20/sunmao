package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmDict;
import com.reip.mdm.service.MdmDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 统一码值字典
 */
@RestController
@RequestMapping("/api/mdm/dicts")
@RequiredArgsConstructor
public class MdmDictController {

    private final MdmDictService svc;

    /** 码值列表（?key=project.status，前端下拉用） */
    @GetMapping
    public Result<List<MdmDict>> list(@RequestParam(required = false) String key) {
        return Result.ok(svc.listByKey(key));
    }

    /** 字典管理分页 */
    @GetMapping("/page")
    public Result<PageResult<MdmDict>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, kw));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmDict dict) {
        return Result.ok(svc.create(dict));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmDict dict) {
        svc.update(dict);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
