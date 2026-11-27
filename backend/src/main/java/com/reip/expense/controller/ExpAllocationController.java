package com.reip.expense.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.expense.entity.ExpAllocation;
import com.reip.expense.service.ExpAllocationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用分摊：规则配置 / 月度分摊计算
 */
@RestController
@RequestMapping("/api/expense/allocations")
@RequiredArgsConstructor
public class ExpAllocationController {

    private final ExpAllocationService svc;

    @GetMapping("/page")
    public Result<PageResult<ExpAllocation>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String month,
                                                  @RequestParam(required = false) Long deptId,
                                                  @RequestParam(required = false) Long projectId) {
        return Result.ok(svc.page(current, size, month, deptId, projectId));
    }

    @GetMapping("/{id}")
    public Result<ExpAllocation> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ExpAllocation a) {
        return Result.ok(svc.create(a));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 月度分摊计算：总额按目标均摊，生成多条分摊记录 */
    @PostMapping("/calculate")
    public Result<Integer> calculate(@RequestBody CalcForm form) {
        return Result.ok(svc.calculate(form.getMonth(), form.getTotal(), form.getDeptIds()));
    }

    @Data
    public static class CalcForm {
        private String month;
        private BigDecimal total;
        private List<Long> deptIds;
    }
}
