package com.reip.mdm.controller;

import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmOrg;
import com.reip.mdm.service.MdmOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织机构
 */
@RestController
@RequestMapping("/api/mdm/orgs")
@RequiredArgsConstructor
public class MdmOrgController {

    private final MdmOrgService svc;

    /** 组织树查询 */
    @GetMapping("/tree")
    public Result<List<MdmOrg>> tree() {
        return Result.ok(svc.tree());
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmOrg org) {
        return Result.ok(svc.create(org));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmOrg org) {
        svc.update(org);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
