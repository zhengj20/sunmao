package com.reip.dwg.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.dwg.entity.DwgChecklist;
import com.reip.dwg.service.DwgChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审查要点库（AI 预审规则源，可配置）
 */
@RestController
@RequestMapping("/api/dwg/checklist")
@RequiredArgsConstructor
public class DwgChecklistController {

    private final DwgChecklistService svc;

    @GetMapping("/page")
    public Result<PageResult<DwgChecklist>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String discipline,
                                                  @RequestParam(required = false) String level,
                                                  @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, discipline, level, kw));
    }

    @GetMapping("/list")
    public Result<List<DwgChecklist>> list() {
        return Result.ok(svc.listAll());
    }

    @PostMapping
    public Result<DwgChecklist> add(@RequestBody DwgChecklistService.Form form) {
        return Result.ok(svc.add(form));
    }

    @PutMapping("/{id}")
    public Result<DwgChecklist> update(@PathVariable Long id, @RequestBody DwgChecklistService.Form form) {
        return Result.ok(svc.update(id, form));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/toggle")
    public Result<DwgChecklist> toggle(@PathVariable Long id) {
        return Result.ok(svc.toggle(id));
    }
}
