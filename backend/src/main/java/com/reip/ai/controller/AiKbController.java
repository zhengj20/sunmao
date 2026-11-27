package com.reip.ai.controller;

import com.reip.ai.entity.AiKbDocument;
import com.reip.ai.service.AiKbService;
import com.reip.ai.service.KbGraphService;
import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库（CRUD + 检索）
 */
@RestController
@RequestMapping("/api/ai/kb")
@RequiredArgsConstructor
public class AiKbController {

    private final AiKbService svc;
    private final KbGraphService graphSvc;

    @GetMapping("/page")
    public Result<PageResult<AiKbDocument>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, category, kw));
    }

    @GetMapping("/search")
    public Result<List<AiKbDocument>> search(@RequestParam String kw,
                                             @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(svc.search(kw, limit));
    }

    @GetMapping("/{id}")
    public Result<AiKbDocument> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody AiKbDocument d) {
        return Result.ok(svc.create(d));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiKbDocument d) {
        svc.update(id, d);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @GetMapping("/graph")
    public Result<Map<String, Object>> graph() {
        return Result.ok(graphSvc.graph());
    }

    @GetMapping("/related/{id}")
    public Result<List<Map<String, Object>>> related(@PathVariable Long id) {
        return Result.ok(graphSvc.related(id));
    }

    @PostMapping("/link")
    public Result<Void> addLink(@RequestBody Map<String, Object> body) {
        Long src = Long.valueOf(body.get("sourceDocId").toString());
        Long tgt = Long.valueOf(body.get("targetDocId").toString());
        String rel = body.getOrDefault("relationType", "RELATED").toString();
        String label = body.get("label") != null ? body.get("label").toString() : null;
        graphSvc.addLink(src, tgt, rel, label);
        return Result.ok();
    }

    @DeleteMapping("/link/{sourceId}/{targetId}")
    public Result<Void> removeLink(@PathVariable Long sourceId, @PathVariable Long targetId) {
        graphSvc.removeLink(sourceId, targetId);
        return Result.ok();
    }

    @GetMapping("/wiki")
    public Result<List<AiKbDocument>> wikiList() {
        return Result.ok(graphSvc.wikiList());
    }
}
