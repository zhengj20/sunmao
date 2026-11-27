package com.reip.sales.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.sales.entity.SaleIncoming;
import com.reip.sales.service.SaleIncomingService;
import com.reip.sales.service.SaleIncomingService.CreateForm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 来账（银企直联/手机短信）：登记收款信息 → 智能/手动匹配到合同·房间 → 核销生成实收
 */
@RestController
@RequestMapping("/api/sales/incoming")
@RequiredArgsConstructor
public class SaleIncomingController {

    private final SaleIncomingService svc;

    @GetMapping("/page")
    public Result<PageResult<SaleIncoming>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(required = false) String matchStatus,
                                                 @RequestParam(required = false) String source) {
        return Result.ok(svc.page(current, size, projectId, matchStatus, source));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.stats(projectId));
    }

    /** 登记一条来账（银企直联/短信） */
    @PostMapping
    public Result<SaleIncoming> create(@RequestBody CreateForm form) {
        return Result.ok(svc.create(form));
    }

    /** 模拟接收银企直联/短信推送（按未收应收生成待匹配来账，演示用） */
    @PostMapping("/simulate")
    public Result<Integer> simulate(@RequestParam Long projectId,
                                    @RequestParam(defaultValue = "BANK") String source,
                                    @RequestParam(defaultValue = "5") int max) {
        return Result.ok(svc.simulate(projectId, source, max));
    }

    /** 智能匹配建议（付款人+金额+房号 → 推荐合同） */
    @GetMapping("/{id}/suggest")
    public Result<Map<String, Object>> suggest(@PathVariable Long id) {
        return Result.ok(svc.suggest(id));
    }

    /** 匹配（手动指定合同或自动取最佳）→ 核销生成实收 */
    @PostMapping("/{id}/match")
    public Result<SaleIncoming> match(@PathVariable Long id, @RequestBody(required = false) MatchForm form) {
        return Result.ok(svc.match(id, form == null ? null : form.getContractId()));
    }

    /** 排除（无法匹配/非本司来账） */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        svc.reject(id);
        return Result.ok();
    }

    @Data
    public static class MatchForm {
        private Long contractId;
    }
}
