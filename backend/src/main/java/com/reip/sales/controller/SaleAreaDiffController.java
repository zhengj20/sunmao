package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleAreaDiff;
import com.reip.sales.service.SaleAreaDiffService;
import com.reip.sales.service.SaleAreaDiffService.CreateForm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 面积补差（多退少补）：导入/手工登记 → 生成差价款 → 少补收款/多退退款 → 结清
 */
@RestController
@RequestMapping("/api/sales/area-diff")
@RequiredArgsConstructor
public class SaleAreaDiffController {

    private final SaleAreaDiffService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleAreaDiff>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String direction,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(svc.page(current, size, projectId, status, direction, keyword));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.stats(projectId));
    }

    @GetMapping("/{id}")
    public Result<SaleAreaDiff> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    /** 手工登记一条补差 */
    @PostMapping
    public Result<SaleAreaDiff> create(@RequestBody CreateForm form) {
        return Result.ok(svc.create(form));
    }

    /** CSV 批量导入（房号,合同面积,实测面积,单价） */
    @PostMapping("/import")
    public Result<Map<String, Object>> importCsv(@RequestBody ImportForm form) {
        return Result.ok(svc.importCsv(form.getProjectId(), form.getCsv(), form.getRemark()));
    }

    /** 生成补差单（少补→应收计划；多退→待退款） */
    @PostMapping("/{id}/settle")
    public Result<SaleAreaDiff> settle(@PathVariable Long id) {
        return Result.ok(svc.settle(id));
    }

    /** 标记已结清（已收款/已退款） */
    @PostMapping("/{id}/settle-done")
    public Result<Void> settleDone(@PathVariable Long id, @RequestBody(required = false) SettleForm form) {
        svc.settleDone(id, form == null ? null : form.getSettled());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @Data
    public static class ImportForm {
        private Long projectId;
        private String csv;
        private String remark;
    }

    @Data
    public static class SettleForm {
        private BigDecimal settled;
    }
}
