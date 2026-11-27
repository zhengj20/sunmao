package com.reip.budget.controller;

import com.reip.budget.entity.BudAdjust;
import com.reip.budget.entity.BudBudget;
import com.reip.budget.entity.BudBudgetMonth;
import com.reip.budget.service.BudBudgetService;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 全面预算：年度预算 + 月度分解 + 调整留痕
 * 规则 B1 年度控制月度 / B2 年度下调约束
 */
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudBudgetController {

    private final BudBudgetService svc;

    // ==================== 年度预算 ====================

    @GetMapping("/annual/page")
    public Result<PageResult<BudBudget>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) Long deptId,
                                              @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, year, type, status, projectId, deptId, kw));
    }

    /** 详情：年度预算 + 月度分解 + 最近调整留痕 */
    @GetMapping("/annual/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping("/annual")
    public Result<Long> create(@RequestBody BudBudget b) {
        return Result.ok(svc.create(b));
    }

    /** 生效（DRAFT→APPROVED，生效后参与管控） */
    @PostMapping("/annual/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        svc.activate(id);
        return Result.ok();
    }

    /** 归档（退出管控） */
    @PostMapping("/annual/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        svc.archive(id);
        return Result.ok();
    }

    @DeleteMapping("/annual/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 年度调整（B2）：{ adjustType: INCREASE/DECREASE, amount, reason } → 调整单ID */
    @PostMapping("/annual/{id}/adjust")
    public Result<Long> adjustAnnual(@PathVariable Long id, @RequestBody Map<String, Object> form) {
        return Result.ok(svc.adjustAnnual(id, str(form.get("adjustType")), dec(form.get("amount")), str(form.get("reason"))));
    }

    // ==================== 月度分解 ====================

    /** 月度分解列表（含执行/余额/超预算标记） */
    @GetMapping("/annual/{id}/months")
    public Result<List<BudBudgetMonth>> months(@PathVariable Long id) {
        return Result.ok(svc.months(id));
    }

    /** 新增/更新某月预算（B1）：{ budgetId, month, amount } */
    @PutMapping("/month")
    public Result<Long> upsertMonth(@RequestBody BudBudgetMonth m) {
        if (m.getBudgetId() == null || m.getMonth() == null) throw new com.reip.common.exception.BizException("请选择预算与月份");
        return Result.ok(svc.upsertMonth(m.getBudgetId(), m.getMonth(), m.getMonthAmount()));
    }

    /** 月度调整（B1 + 不低于已执行）：{ adjustType, amount, reason } → 调整单ID */
    @PostMapping("/month/{id}/adjust")
    public Result<Long> adjustMonth(@PathVariable Long id, @RequestBody Map<String, Object> form) {
        return Result.ok(svc.adjustMonth(id, str(form.get("adjustType")), dec(form.get("amount")), str(form.get("reason"))));
    }

    /** 跨月调剂：{ fromMonth, toMonth, amount, reason } */
    @PostMapping("/annual/{id}/reallocate")
    public Result<Void> reallocate(@PathVariable Long id, @RequestBody Map<String, Object> form) {
        svc.reallocate(id, intOf(form.get("fromMonth")), intOf(form.get("toMonth")),
                dec(form.get("amount")), str(form.get("reason")));
        return Result.ok();
    }

    /** 一键分解：{ mode: EVEN/RATIO, ratios:[12项] } → 生成月数 */
    @PostMapping("/annual/{id}/months/generate")
    public Result<Integer> generateMonths(@PathVariable Long id, @RequestBody Map<String, Object> form) {
        String mode = str(form.get("mode"));
        List<BigDecimal> ratios = form.get("ratios") == null ? null
                : ((List<?>) form.get("ratios")).stream().map(BudBudgetController::dec).toList();
        return Result.ok(svc.generateMonths(id, mode, ratios));
    }

    // ==================== 调整留痕 ====================

    @GetMapping("/adjusts/page")
    public Result<PageResult<BudAdjust>> adjustPage(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long budgetId) {
        return Result.ok(svc.adjustPage(current, size, budgetId));
    }

    // ==================== 工具 ====================

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return null;
        return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
    }

    private static Integer intOf(Object v) {
        return v == null ? null : Integer.valueOf(v.toString());
    }
}
