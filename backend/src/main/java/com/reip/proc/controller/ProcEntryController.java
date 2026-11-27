package com.reip.proc.controller;

import com.reip.common.result.Result;
import com.reip.proc.entity.ProcEntry;
import com.reip.proc.service.ProcEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 入围与考察
 */
@RestController
@RequestMapping("/api/proc/entries")
@RequiredArgsConstructor
public class ProcEntryController {

    private final ProcEntryService svc;

    /** 按招采立项维度列出入围名单 */
    @GetMapping
    public Result<List<ProcEntry>> list(@RequestParam Long procProjectId) {
        return Result.ok(svc.list(procProjectId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ProcEntry entry) {
        return Result.ok(svc.create(entry));
    }

    /** 资格预审结果/考察评分/结论/备选标记更新 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProcEntry entry) {
        entry.setId(id);
        svc.update(entry);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 提交入围名单审批（bizId=procProjectId） */
    @PostMapping("/{procProjectId}/submit")
    public Result<Void> submit(@PathVariable Long procProjectId) {
        svc.submit(procProjectId);
        return Result.ok();
    }
}
