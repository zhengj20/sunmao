package com.reip.proc.controller;

import com.reip.common.result.Result;
import com.reip.proc.entity.ProcBid;
import com.reip.proc.entity.ProcBidItem;
import com.reip.proc.service.ProcBidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回标登记
 */
@RestController
@RequestMapping("/api/proc/bids")
@RequiredArgsConstructor
public class ProcBidController {

    private final ProcBidService svc;

    @GetMapping
    public Result<List<ProcBid>> list(@RequestParam Long tenderId) {
        return Result.ok(svc.list(tenderId));
    }

    @PostMapping
    public Result<Long> register(@RequestBody ProcBid bid) {
        return Result.ok(svc.register(bid));
    }

    // ================= 技术/商务标分离明细 =================
    @GetMapping("/{id}/items")
    public Result<List<ProcBidItem>> listItems(@PathVariable Long id) {
        return Result.ok(svc.listItems(id));
    }

    @PostMapping("/{id}/items")
    public Result<Long> addItem(@PathVariable Long id, @RequestBody ProcBidItem item) {
        return Result.ok(svc.addItem(id, item));
    }

    @PutMapping("/{id}/items")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody ProcBidItem item) {
        svc.updateItem(id, item);
        return Result.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        svc.deleteItem(id, itemId);
        return Result.ok();
    }
}
