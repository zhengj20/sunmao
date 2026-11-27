package com.reip.dwg.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.dwg.entity.DwgDrawing;
import com.reip.dwg.entity.DwgIssue;
import com.reip.dwg.service.DwgDrawingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 设计审图（读图/审图）：上传图纸 → AI/系统预审 → 人工逐条提出/确认 → 逐条解决 → 通过/驳回
 */
@RestController
@RequestMapping("/api/dwg")
@RequiredArgsConstructor
public class DwgController {

    private final DwgDrawingService svc;

    // ==================== 图纸审查单 ====================

    /** 上传图纸并创建审查单（multipart：file + 元信息） */
    @PostMapping("/drawings")
    public Result<DwgDrawing> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam Long projectId,
                                     @RequestParam String discipline,
                                     @RequestParam String title,
                                     @RequestParam(required = false) String version,
                                     @RequestParam(required = false) String stage,
                                     @RequestParam(required = false) String designUnit,
                                     @RequestParam(required = false) String remark) {
        return Result.ok(svc.upload(file, projectId, discipline, title, version, stage, designUnit, remark));
    }

    @GetMapping("/drawings/page")
    public Result<PageResult<DwgDrawing>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String discipline,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, discipline, status, kw));
    }

    /** 详情：审查单 + 问题清单 + 统计 */
    @GetMapping("/drawings/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @DeleteMapping("/drawings/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    // ==================== AI / 系统预审 ====================

    /** 运行 AI/系统预审：生成审查要点(AI 问题) + 预审意见 */
    @PostMapping("/drawings/{id}/ai-review")
    public Result<Map<String, Object>> aiReview(@PathVariable Long id) {
        return Result.ok(svc.aiReview(id));
    }

    // ==================== 问题管理 ====================

    /** 人工提出问题 */
    @PostMapping("/drawings/{id}/issues")
    public Result<DwgIssue> addIssue(@PathVariable Long id, @RequestBody DwgDrawingService.IssueForm form) {
        return Result.ok(svc.addIssue(id, form));
    }

    /** 编辑问题 */
    @PutMapping("/issues/{id}")
    public Result<DwgIssue> updateIssue(@PathVariable Long id, @RequestBody DwgDrawingService.IssueForm form) {
        return Result.ok(svc.updateIssue(id, form));
    }

    /** 标记已解决（附解决说明） */
    @PostMapping("/issues/{id}/resolve")
    public Result<DwgIssue> resolve(@PathVariable Long id, @RequestBody(required = false) NoteForm form) {
        return Result.ok(svc.resolveIssue(id, form == null ? null : form.getNote()));
    }

    /** 标记无需处理（审查人判定不适用/误报） */
    @PostMapping("/issues/{id}/dismiss")
    public Result<DwgIssue> dismiss(@PathVariable Long id) {
        return Result.ok(svc.dismissIssue(id));
    }

    /** 删除一条未解决的问题 */
    @DeleteMapping("/issues/{id}")
    public Result<Void> deleteIssue(@PathVariable Long id) {
        svc.deleteIssue(id);
        return Result.ok();
    }

    // ==================== 审查结论 ====================

    /** 审查通过（要求无严重级未解决问题） */
    @PostMapping("/drawings/{id}/pass")
    public Result<DwgDrawing> pass(@PathVariable Long id) {
        return Result.ok(svc.passDrawing(id));
    }

    /** 审查驳回（附原因，退回整改） */
    @PostMapping("/drawings/{id}/reject")
    public Result<DwgDrawing> reject(@PathVariable Long id, @RequestBody(required = false) NoteForm form) {
        return Result.ok(svc.rejectDrawing(id, form == null ? null : form.getReason()));
    }

    // ==================== 汇总 ====================

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.summary(projectId));
    }

    @Data
    public static class NoteForm {
        private String note;
        private String reason;
    }
}
