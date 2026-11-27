package com.reip.mdm.controller;

import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmCostAccount;
import com.reip.mdm.service.MdmCostAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 成本科目（集团模板 + 项目克隆树）
 */
@RestController
@RequestMapping("/api/mdm/accounts")
@RequiredArgsConstructor
public class MdmCostAccountController {

    private final MdmCostAccountService svc;

    /** 科目树：projectId 为空返回集团模板树，否则返回项目克隆树 */
    @GetMapping("/tree")
    public Result<List<MdmCostAccount>> tree(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.tree(projectId));
    }

    /** 集团模板克隆到项目：{projectId} */
    @PostMapping("/clone")
    public Result<Integer> clone(@RequestBody Map<String, Long> body) {
        return Result.ok(svc.cloneToProject(body.get("projectId")));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmCostAccount account) {
        return Result.ok(svc.create(account));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmCostAccount account) {
        svc.update(account);
        return Result.ok();
    }

    /** 科目删除（有子节点禁删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }
}
