package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmSupplier;
import com.reip.mdm.entity.MdmSupplierQualification;
import com.reip.mdm.service.MdmSupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 供应商（档案/资质证照/拉黑/下拉选项）
 */
@RestController
@RequestMapping("/api/mdm/suppliers")
@RequiredArgsConstructor
public class MdmSupplierController {

    private final MdmSupplierService svc;

    /** 供应商分页 */
    @GetMapping("/page")
    public Result<PageResult<MdmSupplier>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String kw,
                                                @RequestParam(required = false) String libType) {
        return Result.ok(svc.page(current, size, kw, libType));
    }

    /** 供应商下拉选项（全部） */
    @GetMapping("/options")
    public Result<List<MdmSupplier>> options() {
        return Result.ok(svc.options(false));
    }

    /** 合格供应商下拉选项（排除黑名单） */
    @GetMapping("/qualified-options")
    public Result<List<MdmSupplier>> qualifiedOptions() {
        return Result.ok(svc.options(true));
    }

    /** 资质到期提醒：days 天内到期（默认90） */
    @GetMapping("/qualifications/expiring")
    public Result<List<MdmSupplierQualification>> expiring(@RequestParam(defaultValue = "90") int days) {
        return Result.ok(svc.expiring(days));
    }

    @GetMapping("/{id}")
    public Result<MdmSupplier> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新增供应商（supplier_code 自动生成） */
    @PostMapping
    public Result<Long> create(@RequestBody MdmSupplier supplier) {
        return Result.ok(svc.create(supplier));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmSupplier supplier) {
        svc.update(supplier);
        return Result.ok();
    }

    /** 删除保护：被业务引用的供应商禁止删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 拉黑：{reason} */
    @PostMapping("/{id}/blacklist")
    public Result<Void> blacklist(@PathVariable Long id, @RequestBody Map<String, String> body) {
        svc.blacklist(id, body.get("reason"));
        return Result.ok();
    }

    /** 恢复到预选库 */
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        svc.restore(id);
        return Result.ok();
    }

    // ================= 资质证照 =================

    @GetMapping("/{id}/qualifications")
    public Result<List<MdmSupplierQualification>> qualificationList(@PathVariable Long id) {
        return Result.ok(svc.qualificationList(id));
    }

    @PostMapping("/{id}/qualifications")
    public Result<Long> createQualification(@PathVariable Long id, @RequestBody MdmSupplierQualification qual) {
        return Result.ok(svc.createQualification(id, qual));
    }

    @PutMapping("/{id}/qualifications/{qualId}")
    public Result<Void> updateQualification(@PathVariable Long id, @PathVariable Long qualId,
                                            @RequestBody MdmSupplierQualification qual) {
        svc.updateQualification(id, qualId, qual);
        return Result.ok();
    }

    @DeleteMapping("/{id}/qualifications/{qualId}")
    public Result<Void> deleteQualification(@PathVariable Long id, @PathVariable Long qualId) {
        svc.deleteQualification(id, qualId);
        return Result.ok();
    }
}
