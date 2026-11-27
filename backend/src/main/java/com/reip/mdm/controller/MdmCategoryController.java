package com.reip.mdm.controller;

import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmCategory;
import com.reip.mdm.service.MdmCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类树（合同分类/采购品类/变更分类/费用科目）
 */
@RestController
@RequestMapping("/api/mdm/categories")
@RequiredArgsConstructor
public class MdmCategoryController {

    private final MdmCategoryService svc;

    /** 分类树：type=CONTRACT|PURCHASE|CHANGE|EXPENSE */
    @GetMapping("/tree")
    public Result<List<MdmCategory>> tree(@RequestParam(required = false) String type) {
        return Result.ok(svc.tree(type));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmCategory category) {
        return Result.ok(svc.create(category));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmCategory category) {
        svc.update(category);
        return Result.ok();
    }

    /** 分类删除（有子节点禁删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
