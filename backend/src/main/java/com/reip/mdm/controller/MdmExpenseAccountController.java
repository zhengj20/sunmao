package com.reip.mdm.controller;

import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmExpenseAccount;
import com.reip.mdm.service.MdmExpenseAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 费用科目（三费：管理/销售/财务，与成本科目分离）
 */
@RestController
@RequestMapping("/api/mdm/expense-accounts")
@RequiredArgsConstructor
public class MdmExpenseAccountController {

    private final MdmExpenseAccountService svc;

    /** 费用科目树（集团级） */
    @GetMapping("/tree")
    public Result<List<MdmExpenseAccount>> tree() {
        return Result.ok(svc.tree());
    }

    @PostMapping
    public Result<Long> create(@RequestBody MdmExpenseAccount account) {
        return Result.ok(svc.create(account));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmExpenseAccount account) {
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
