package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmBuilding;
import com.reip.mdm.entity.MdmPhase;
import com.reip.mdm.entity.MdmProductType;
import com.reip.mdm.entity.MdmProject;
import com.reip.mdm.service.MdmProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目主数据（含分期/业态/楼栋子资源）
 */
@RestController
@RequestMapping("/api/mdm/projects")
@RequiredArgsConstructor
public class MdmProjectController {

    private final MdmProjectService svc;

    /** 项目分页 */
    @GetMapping("/page")
    public Result<PageResult<MdmProject>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String kw,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, kw, status));
    }

    /** 项目下拉选项（id/projectCode/name/status） */
    @GetMapping("/options")
    public Result<List<MdmProject>> options() {
        return Result.ok(svc.options());
    }

    @GetMapping("/{id}")
    public Result<MdmProject> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 新增项目（project_code 自动生成） */
    @PostMapping
    public Result<Long> create(@RequestBody MdmProject project) {
        return Result.ok(svc.create(project));
    }

    @PutMapping
    public Result<Void> update(@RequestBody MdmProject project) {
        svc.update(project);
        return Result.ok();
    }

    /** 状态迁移：PREPARING→CONSTRUCTION→DELIVERED→LIQUIDATION */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        svc.changeStatus(id, status);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    // ================= 分期 =================

    @GetMapping("/{id}/phases")
    public Result<List<MdmPhase>> phaseList(@PathVariable Long id) {
        return Result.ok(svc.phaseList(id));
    }

    @PostMapping("/{id}/phases")
    public Result<Long> createPhase(@PathVariable Long id, @RequestBody MdmPhase phase) {
        return Result.ok(svc.createPhase(id, phase));
    }

    @PutMapping("/{id}/phases/{phaseId}")
    public Result<Void> updatePhase(@PathVariable Long id, @PathVariable Long phaseId, @RequestBody MdmPhase phase) {
        svc.updatePhase(id, phaseId, phase);
        return Result.ok();
    }

    @DeleteMapping("/{id}/phases/{phaseId}")
    public Result<Void> deletePhase(@PathVariable Long id, @PathVariable Long phaseId) {
        svc.deletePhase(id, phaseId);
        return Result.ok();
    }

    // ================= 业态 =================

    @GetMapping("/{id}/product-types")
    public Result<List<MdmProductType>> productTypeList(@PathVariable Long id) {
        return Result.ok(svc.productTypeList(id));
    }

    @PostMapping("/{id}/product-types")
    public Result<Long> createProductType(@PathVariable Long id, @RequestBody MdmProductType productType) {
        return Result.ok(svc.createProductType(id, productType));
    }

    @PutMapping("/{id}/product-types/{productTypeId}")
    public Result<Void> updateProductType(@PathVariable Long id, @PathVariable Long productTypeId,
                                          @RequestBody MdmProductType productType) {
        svc.updateProductType(id, productTypeId, productType);
        return Result.ok();
    }

    @DeleteMapping("/{id}/product-types/{productTypeId}")
    public Result<Void> deleteProductType(@PathVariable Long id, @PathVariable Long productTypeId) {
        svc.deleteProductType(id, productTypeId);
        return Result.ok();
    }

    // ================= 楼栋 =================

    @GetMapping("/{id}/buildings")
    public Result<List<MdmBuilding>> buildingList(@PathVariable Long id) {
        return Result.ok(svc.buildingList(id));
    }

    @PostMapping("/{id}/buildings")
    public Result<Long> createBuilding(@PathVariable Long id, @RequestBody MdmBuilding building) {
        return Result.ok(svc.createBuilding(id, building));
    }

    @PutMapping("/{id}/buildings/{buildingId}")
    public Result<Void> updateBuilding(@PathVariable Long id, @PathVariable Long buildingId,
                                       @RequestBody MdmBuilding building) {
        svc.updateBuilding(id, buildingId, building);
        return Result.ok();
    }

    @DeleteMapping("/{id}/buildings/{buildingId}")
    public Result<Void> deleteBuilding(@PathVariable Long id, @PathVariable Long buildingId) {
        svc.deleteBuilding(id, buildingId);
        return Result.ok();
    }
}
