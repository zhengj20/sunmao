package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmCustomer;
import com.reip.mdm.entity.MdmCustomerRelative;
import com.reip.mdm.service.MdmCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户（档案/关联人）
 */
@RestController
@RequestMapping("/api/mdm/customers")
@RequiredArgsConstructor
public class MdmCustomerController {

    private final MdmCustomerService svc;

    /** 客户分页 */
    @GetMapping("/page")
    public Result<PageResult<MdmCustomer>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String kw,
                                                @RequestParam(required = false) String customerType) {
        return Result.ok(svc.page(current, size, kw, customerType));
    }

    @GetMapping("/{id}")
    public Result<MdmCustomer> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新增客户（customer_code 自动生成） */
    @PostMapping
    public Result<Long> create(@RequestBody MdmCustomer customer) {
        return Result.ok(svc.create(customer));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmCustomer customer) {
        svc.update(customer);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    // ================= 关联人 =================

    @GetMapping("/{id}/relatives")
    public Result<List<MdmCustomerRelative>> relativeList(@PathVariable Long id) {
        return Result.ok(svc.relativeList(id));
    }

    @PostMapping("/{id}/relatives")
    public Result<Long> createRelative(@PathVariable Long id, @RequestBody MdmCustomerRelative relative) {
        return Result.ok(svc.createRelative(id, relative));
    }

    @PutMapping("/{id}/relatives/{relativeId}")
    public Result<Void> updateRelative(@PathVariable Long id, @PathVariable Long relativeId,
                                       @RequestBody MdmCustomerRelative relative) {
        svc.updateRelative(id, relativeId, relative);
        return Result.ok();
    }

    @DeleteMapping("/{id}/relatives/{relativeId}")
    public Result<Void> deleteRelative(@PathVariable Long id, @PathVariable Long relativeId) {
        svc.deleteRelative(id, relativeId);
        return Result.ok();
    }
}
