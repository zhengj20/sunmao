package com.reip.proc.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.proc.entity.ProcTender;
import com.reip.proc.service.ProcBidService;
import com.reip.proc.service.ProcTenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 发标管理（含清标入口）
 */
@RestController
@RequestMapping("/api/proc/tenders")
@RequiredArgsConstructor
public class ProcTenderController {

    private final ProcTenderService svc;
    private final ProcBidService bidSvc;

    @GetMapping("/page")
    public Result<PageResult<ProcTender>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long procProjectId,
                                               @RequestParam(required = false) String status) {
        return Result.ok(svc.page(current, size, procProjectId, status));
    }

    @GetMapping("/{id}")
    public Result<ProcTender> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcTender tender) {
        return Result.ok(svc.create(tender));
    }

    /** 开标：PUBLISHED/CLARIFY→OPENED */
    @PutMapping("/{id}/open")
    public Result<Void> open(@PathVariable Long id) {
        svc.open(id);
        return Result.ok();
    }

    /** 截止关闭 */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        svc.close(id);
        return Result.ok();
    }

    /** 答疑：追加到 clarify_notes JSON 数组 */
    @PutMapping("/{id}/clarify")
    public Result<Void> clarify(@PathVariable Long id, @RequestBody Map<String, String> body) {
        svc.clarify(id, body.get("question"), body.get("answer"));
        return Result.ok();
    }

    /** 保证金退还 */
    @PutMapping("/{id}/refund-deposit")
    public Result<Void> refundDeposit(@PathVariable Long id) {
        svc.refundDeposit(id);
        return Result.ok();
    }

    /** 清标：不平衡报价检测 + 围串标同IP预警，返回检测报告 */
    @PostMapping("/{id}/clear")
    public Result<Map<String, Object>> clear(@PathVariable Long id) {
        return Result.ok(bidSvc.clear(id));
    }
}
