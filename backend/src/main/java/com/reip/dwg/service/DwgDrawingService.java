package com.reip.dwg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.ai.entity.AiPrompt;
import com.reip.ai.mapper.AiMappers;
import com.reip.ai.service.AiAuditService;
import com.reip.ai.service.AiGuardrail;
import com.reip.ai.service.AiLlmGateway;
import com.reip.ai.service.AiLlmResult;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.dwg.entity.DwgChecklist;
import com.reip.dwg.entity.DwgDrawing;
import com.reip.dwg.entity.DwgIssue;
import com.reip.dwg.mapper.DwgMappers;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 设计审图（读图/审图）核心服务。
 *
 * 流程：上传图纸 → AI/系统预审(生成审查要点) → 人工逐条提出/确认 → 逐条解决 → 通过/驳回。
 *
 * AI 原则（同 AiApprovalService）：
 *  - 规则引擎（无 LLM 也能用）：按专业产出确定性「审查要点」作为 AI 问题(source=AI)；
 *  - LLM（可选）：生成自然语言「预审意见」增强，无模型/失败自动降级为 RULE；
 *  - 护栏：AI 仅提示、不代审，最终通过/驳回由人工决定；每次调用留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DwgDrawingService {

    private static final Set<String> ALLOWED_EXT = Set.of("dwg", "dxf", "png", "jpg", "jpeg", "pdf");

    private final DwgMappers.DrawingMapper drawingMapper;
    private final DwgMappers.IssueMapper issueMapper;
    private final DwgMappers.ChecklistMapper checklistMapper;
    private final DwgMappers.MdmLookupMapper mdmLookup;
    private final CodeRuleService codeRuleService;
    private final AiLlmGateway gateway;
    private final AiGuardrail guardrail;
    private final AiAuditService audit;
    private final AiMappers.PromptMapper promptMapper;

    @Value("${reip.upload.base-path:./uploads}")
    private String basePath;

    // ==================== 上传 ====================

    @Transactional
    public DwgDrawing upload(MultipartFile file, Long projectId, String discipline, String title,
                             String version, String stage, String designUnit, String remark) {
        if (file == null || file.isEmpty()) throw new BizException("请选择要上传的设计图纸文件");
        if (projectId == null) throw new BizException("请选择项目");
        if (mdmLookup.selectProjectName(projectId) == null) throw new BizException("项目不存在：" + projectId);
        if (discipline == null || discipline.isBlank()) throw new BizException("请选择专业");
        if (title == null || title.isBlank()) throw new BizException("请填写图纸标题");

        String orig = file.getOriginalFilename() == null ? "drawing.dwg" : file.getOriginalFilename();
        String ext = extOf(orig);
        if (ext == null) throw new BizException("仅支持 " + ALLOWED_EXT + " 格式的图纸文件");

        String rel = "dwg/" + UUID.randomUUID() + "_" + orig;
        try {
            File dir = new File(basePath, "dwg");
            if (!dir.exists()) dir.mkdirs();
            file.transferTo(new File(basePath, rel).getAbsoluteFile());
        } catch (Exception e) {
            throw new BizException("图纸文件保存失败：" + e.getMessage());
        }

        DwgDrawing d = new DwgDrawing();
        d.setDrawingCode(codeRuleService.next("STG", Map.of()));
        d.setProjectId(projectId);
        d.setDiscipline(discipline.trim().toUpperCase(Locale.ROOT));
        d.setTitle(title.trim());
        d.setDrawingVersion(blankToNull(version));
        d.setStage(blankToNull(stage));
        d.setDesignUnit(blankToNull(designUnit));
        d.setFileName(orig);
        d.setFilePath(rel);
        d.setFileSize(file.getSize());
        d.setStatus("PENDING");
        d.setRemark(blankToNull(remark));
        drawingMapper.insert(d);
        return d;
    }

    // ==================== 查询 ====================

    public PageResult<DwgDrawing> page(long current, long size, Long projectId, String discipline, String status, String kw) {
        LambdaQueryWrapper<DwgDrawing> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(DwgDrawing::getProjectId, projectId);
        if (discipline != null && !discipline.isBlank()) qw.eq(DwgDrawing::getDiscipline, discipline.trim().toUpperCase(Locale.ROOT));
        if (status != null && !status.isBlank()) qw.eq(DwgDrawing::getStatus, status.trim().toUpperCase(Locale.ROOT));
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(DwgDrawing::getDrawingCode, kw).or().like(DwgDrawing::getTitle, kw));
        qw.orderByDesc(DwgDrawing::getId);
        Page<DwgDrawing> p = drawingMapper.selectPage(new Page<>(current, size), qw);
        List<DwgDrawing> records = p.getRecords();
        enrich(records);
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 详情：审查单 + 问题清单 */
    public Map<String, Object> detail(Long id) {
        DwgDrawing d = require(id);
        enrich(List.of(d));
        List<DwgIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<DwgIssue>()
                .eq(DwgIssue::getDrawingId, id)
                .orderByAsc(DwgIssue::getLevel)
                .orderByAsc(DwgIssue::getSeq));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drawing", d);
        out.put("issues", issues);
        out.put("advisory", guardrail.advisory());
        return out;
    }

    @Transactional
    public void delete(Long id) {
        DwgDrawing d = require(id);
        if (!"PENDING".equals(d.getStatus())) {
            long open = issueMapper.selectCount(new LambdaQueryWrapper<DwgIssue>()
                    .eq(DwgIssue::getDrawingId, id).eq(DwgIssue::getStatus, "OPEN"));
            if (open > 0) throw new BizException("该审查单尚有未解决问题，不能删除");
        }
        File f = resolve(d.getFilePath());
        if (f != null && f.exists()) f.delete();
        issueMapper.delete(new LambdaQueryWrapper<DwgIssue>().eq(DwgIssue::getDrawingId, id));
        drawingMapper.deleteById(id);
    }

    // ==================== AI / 系统预审 ====================

    /**
     * AI/系统预审：规则引擎产出审查要点(AI 问题) + LLM 生成预审意见(可选)。
     * 重跑会清理该单旧的 AI 要点并重新生成，人工问题保留。
     */
    public Map<String, Object> aiReview(Long id) {
        DwgDrawing d = require(id);
        if ("PASSED".equals(d.getStatus())) throw new BizException("该图纸已审查通过，如需复审请先驳回");
        String projectName = mdmLookup.selectProjectName(d.getProjectId());
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("projectName", projectName == null ? "未知" : projectName);
        ctx.put("discipline", disciplineLabel(d.getDiscipline()));
        ctx.put("stage", stageLabel(d.getStage()));
        ctx.put("title", d.getTitle());
        ctx.put("version", d.getDrawingVersion() == null ? "-" : d.getDrawingVersion());
        ctx.put("fileName", d.getFileName());
        ctx.put("fileSize", fmtSize(d.getFileSize()));

        // 1) 规则引擎：确定性审查要点（读取「审查要点库」，无数据回退内置规则；无 LLM 也能用）
        List<DwgIssue> points = rulePointsFromLib(d.getDiscipline(), d.getStage());

        // 2) LLM 增强（可选）：生成自然语言预审意见
        String aiMode = "RULE";
        String note = ruleSummary(d, points);
        Long configId = gateway.findActive() == null ? null : gateway.findActive().getId();
        Integer tokenIn = null, tokenOut = null;
        long t0 = System.currentTimeMillis();
        String callStatus = "DEGRADED";
        String inputDigest = ctx.get("projectName") + " / " + ctx.get("discipline") + " / " + d.getTitle();
        try {
            AiPrompt prompt = findPrompt("DRAWING_REVIEW");
            if (prompt != null) {
                Map<String, Object> pctx = new LinkedHashMap<>(ctx);
                pctx.put("points", points.stream()
                        .map(x -> x.getLevel() + ":" + x.getDescription())
                        .reduce((a, b) -> a + "；" + b).orElse("无"));
                AiLlmResult res = gateway.chat(prompt, pctx);
                if (res.isSuccess() && !guardrail.isLowConfidence(res.getContent())) {
                    aiMode = "LLM";
                    note = res.getContent();
                    tokenIn = res.getTokenIn();
                    tokenOut = res.getTokenOut();
                    callStatus = "SUCCESS";
                }
            }
        } catch (Exception e) {
            log.warn("[dwg] LLM 预审失败，回退规则引擎: {}", e.getMessage());
        }

        // 3) 留痕（R3）
        audit.record("DRAWING_REVIEW", aiMode, configId, inputDigest, note,
                tokenIn, tokenOut, null, System.currentTimeMillis() - t0, callStatus);

        // 4) 落库：清理旧 AI 要点 → 写入新要点 → 更新审查单
        issueMapper.deleteByDrawingAndSource(id, "AI");
        int seq = 1;
        for (DwgIssue x : points) {
            x.setId(null);
            x.setDrawingId(id);
            x.setSource("AI");
            x.setSeq(seq++);
            x.setStatus("OPEN");
            issueMapper.insert(x);
        }
        d.setAiMode(aiMode);
        d.setAiNote(note);
        d.setStatus("REVIEWING");
        if (d.getReviewerName() == null) d.setReviewerName(UserContext.currentRealName());
        drawingMapper.updateById(d);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drawingId", id);
        out.put("aiMode", aiMode);
        out.put("aiNote", note);
        out.put("pointCount", points.size());
        out.put("advisory", guardrail.advisory());
        return out;
    }

    // ==================== 问题管理（人工提出 / 逐条解决） ====================

    @Transactional
    public DwgIssue addIssue(Long drawingId, IssueForm form) {
        DwgDrawing d = require(drawingId);
        DwgIssue i = new DwgIssue();
        i.setDrawingId(drawingId);
        i.setSource("MANUAL");
        i.setLevel(form.getLevel() == null || form.getLevel().isBlank() ? "MEDIUM" : form.getLevel().trim().toUpperCase(Locale.ROOT));
        i.setCategory(blankToNull(form.getCategory()));
        i.setLocation(blankToNull(form.getLocation()));
        i.setDescription(form.getDescription());
        i.setSuggestion(blankToNull(form.getSuggestion()));
        i.setAssigneeName(blankToNull(form.getAssigneeName()));
        i.setMarkerX(form.getMarkerX());
        i.setMarkerY(form.getMarkerY());
        i.setStatus("OPEN");
        Integer maxSeq = issueMapper.selectObjs(new LambdaQueryWrapper<DwgIssue>()
                .select(DwgIssue::getSeq).eq(DwgIssue::getDrawingId, drawingId)).stream()
                .map(o -> { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; } })
                .max(Integer::compare).orElse(0);
        i.setSeq(maxSeq + 1);
        issueMapper.insert(i);
        if ("PENDING".equals(d.getStatus())) {
            d.setStatus("REVIEWING");
            if (d.getReviewerName() == null) d.setReviewerName(UserContext.currentRealName());
            drawingMapper.updateById(d);
        }
        return i;
    }

    @Transactional
    public DwgIssue updateIssue(Long issueId, IssueForm form) {
        DwgIssue i = requireIssue(issueId);
        if (form.getLevel() != null && !form.getLevel().isBlank()) i.setLevel(form.getLevel().trim().toUpperCase(Locale.ROOT));
        if (form.getCategory() != null) i.setCategory(blankToNull(form.getCategory()));
        if (form.getLocation() != null) i.setLocation(blankToNull(form.getLocation()));
        if (form.getDescription() != null && !form.getDescription().isBlank()) i.setDescription(form.getDescription());
        if (form.getSuggestion() != null) i.setSuggestion(blankToNull(form.getSuggestion()));
        if (form.getAssigneeName() != null) i.setAssigneeName(blankToNull(form.getAssigneeName()));
        if (form.getMarkerX() != null) i.setMarkerX(form.getMarkerX());
        if (form.getMarkerY() != null) i.setMarkerY(form.getMarkerY());
        if (issueMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return i;
    }

    /** 标记已解决（附解决说明） */
    @Transactional
    public DwgIssue resolveIssue(Long issueId, String note) {
        DwgIssue i = requireIssue(issueId);
        i.setStatus("RESOLVED");
        i.setResolveNote(blankToNull(note));
        i.setResolverId(UserContext.currentUserId());
        i.setResolveDate(LocalDate.now());
        if (issueMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        autoCloseIfAllResolved(i.getDrawingId());
        return i;
    }

    /** 标记无需处理（审查人判定该要点不适用 / 误报） */
    @Transactional
    public DwgIssue dismissIssue(Long issueId) {
        DwgIssue i = requireIssue(issueId);
        i.setStatus("REJECTED");
        if (issueMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return i;
    }

    /** 删除一条未解决的问题（如移除误报的 AI 要点） */
    @Transactional
    public void deleteIssue(Long issueId) {
        DwgIssue i = requireIssue(issueId);
        if (!"OPEN".equals(i.getStatus())) throw new BizException("仅可删除未解决的问题");
        issueMapper.deleteById(issueId);
    }

    // ==================== 审查结论 ====================

    /** 审查通过：要求无「严重」级未解决问题（一般/建议可放行） */
    @Transactional
    public DwgDrawing passDrawing(Long id) {
        DwgDrawing d = require(id);
        List<DwgIssue> openHigh = issueMapper.selectList(new LambdaQueryWrapper<DwgIssue>()
                .eq(DwgIssue::getDrawingId, id)
                .eq(DwgIssue::getStatus, "OPEN")
                .eq(DwgIssue::getLevel, "HIGH"));
        if (!openHigh.isEmpty()) throw new BizException("尚有 " + openHigh.size() + " 条「严重」问题未解决，不能通过审查");
        d.setStatus("PASSED");
        if (d.getReviewerName() == null) d.setReviewerName(UserContext.currentRealName());
        if (drawingMapper.updateById(d) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return d;
    }

    /** 审查驳回（附原因），退回设计单位整改 */
    @Transactional
    public DwgDrawing rejectDrawing(Long id, String reason) {
        DwgDrawing d = require(id);
        if (reason == null || reason.isBlank()) throw new BizException("请填写驳回原因");
        d.setStatus("REJECTED");
        if (d.getReviewerName() == null) d.setReviewerName(UserContext.currentRealName());
        d.setRemark(mergeRemark(d.getRemark(), "驳回：" + reason.trim()));
        if (drawingMapper.updateById(d) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return d;
    }

    // ==================== 汇总 ====================

    public Map<String, Object> summary(Long projectId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        LambdaQueryWrapper<DwgDrawing> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(DwgDrawing::getProjectId, projectId);
        qw.orderByDesc(DwgDrawing::getId);
        List<DwgDrawing> drawings = drawingMapper.selectList(qw);
        List<Long> allIds = drawings.stream().map(DwgDrawing::getId).toList();
        Map<Long, Map<String, Object>> counts = allIds.isEmpty() ? Map.of()
                : issueMapper.countsByDrawing(allIds).stream()
                        .collect(java.util.stream.Collectors.toMap(m -> toLong(m.get("drawingId")), m -> m, (a, b) -> a));
        long totalDrawings = drawings.size();
        long passed = drawings.stream().filter(x -> "PASSED".equals(x.getStatus())).count();
        long rejected = drawings.stream().filter(x -> "REJECTED".equals(x.getStatus())).count();
        long totalIssues = 0, openIssues = 0, resolvedIssues = 0;
        Map<String, Object> out = new LinkedHashMap<>();
        for (DwgDrawing dd : drawings) {
            Map<String, Object> c = counts.get(dd.getId());
            long t = c == null ? 0 : toLong(c.get("total"));
            long o = c == null ? 0 : toLong(c.get("open"));
            long r = c == null ? 0 : toLong(c.get("resolved"));
            totalIssues += t; openIssues += o; resolvedIssues += r;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("drawingCode", dd.getDrawingCode());
            row.put("title", dd.getTitle());
            row.put("discipline", dd.getDiscipline());
            row.put("status", dd.getStatus());
            row.put("total", t); row.put("open", o); row.put("resolved", r);
            rows.add(row);
        }
        out.put("projectId", projectId);
        out.put("totalDrawings", totalDrawings);
        out.put("passed", passed);
        out.put("rejected", rejected);
        out.put("inReview", totalDrawings - passed - rejected);
        out.put("totalIssues", totalIssues);
        out.put("openIssues", openIssues);
        out.put("resolvedIssues", resolvedIssues);
        out.put("rows", rows);
        return out;
    }

    // ==================== 规则引擎：专业审查要点 ====================

    /** 审查要点条目（level / category / description / suggestion） */
    @Data
    private static class Point {
        String level; String category; String desc; String sug;
        Point(String level, String category, String desc, String sug) {
            this.level = level; this.category = category; this.desc = desc; this.sug = sug;
        }
    }

    /** 从「审查要点库」加载审查要点；库无启用数据时回退内置规则 {@link #rulePoints} */
    private List<DwgIssue> rulePointsFromLib(String discipline, String stage) {
        String disc = discipline == null ? "" : discipline.trim().toUpperCase(Locale.ROOT);
        String st = stage == null ? null : stage.trim().toUpperCase(Locale.ROOT);
        LambdaQueryWrapper<DwgChecklist> qw = new LambdaQueryWrapper<DwgChecklist>()
                .and(w -> w.eq(DwgChecklist::getDiscipline, "ALL").or().eq(DwgChecklist::getDiscipline, disc))
                .eq(DwgChecklist::getEnabled, 1);
        if (st != null) {
            qw.and(w -> w.isNull(DwgChecklist::getStage).or().eq(DwgChecklist::getStage, st));
        } else {
            qw.isNull(DwgChecklist::getStage);
        }
        qw.orderByAsc(DwgChecklist::getSort).orderByAsc(DwgChecklist::getId);
        List<DwgChecklist> rows = checklistMapper.selectList(qw);
        if (rows.isEmpty()) return rulePoints(disc, st);
        List<DwgIssue> out = new ArrayList<>();
        for (DwgChecklist c : rows) {
            DwgIssue i = new DwgIssue();
            i.setLevel(c.getLevel());
            i.setCategory(c.getCategory());
            i.setDescription(c.getDescription());
            i.setSuggestion(c.getSuggestion());
            out.add(i);
        }
        return out;
    }

    private List<DwgIssue> rulePoints(String discipline, String stage) {
        List<Point> ps = new ArrayList<>();
        // 通用要点（所有专业）
        ps.add(new Point("MEDIUM", "规范符合性", "核对图纸目录、总说明、设计依据与所引规范是否为现行有效版本", "补充或更新规范版本及设计依据清单"));
        ps.add(new Point("MEDIUM", "图面表达", "检查图框、图签、版本号、比例、图例是否完整规范，标注是否清晰可读", "补齐图签要素并核对版本号一致性"));
        // 专业要点
        switch (discipline == null ? "" : discipline.toUpperCase(Locale.ROOT)) {
            case "ARCH" -> ps.addAll(List.of(
                    new Point("HIGH", "安全消防", "核对防火分区划分、安全出口数量与疏散距离是否满足规范要求", "复核疏散距离与防火分区，必要时调整平面布局"),
                    new Point("HIGH", "安全消防", "检查无障碍设计（坡道、扶手、无障碍卫生间）是否齐备", "补充无障碍设计做法"),
                    new Point("MEDIUM", "规范符合性", "核对层高/净高、门窗开启方向、防坠落与栏杆高度", "核对竖向尺寸与栏杆做法"),
                    new Point("MEDIUM", "材料设备", "核对屋面/外墙/卫生间防水做法与材料选型", "明确防水材料与节点做法"),
                    new Point("LOW", "专业冲突", "核对建筑平面与结构、机电预留洞及设备基础是否存在冲突(错漏碰缺)", "与结构/机电核对预留预留洞口")));
            case "STRUCT" -> ps.addAll(List.of(
                    new Point("HIGH", "规范符合性", "核对基础形式与地勘报告持力层、地下水位是否匹配", "复核地勘参数与基础选型"),
                    new Point("HIGH", "规范符合性", "检查最小配筋率、钢筋锚固/搭接长度与节点构造", "复核配筋与节点详图"),
                    new Point("MEDIUM", "规范符合性", "核对混凝土保护层、裂缝控制与抗震构造措施", "核对保护层与抗震等级要求"),
                    new Point("MEDIUM", "尺寸标注", "核对梁柱截面、结构标高与轴线尺寸标注是否完整准确", "补齐结构标高与尺寸标注"),
                    new Point("LOW", "专业冲突", "核对结构预留洞口与机电管线路由是否冲突", "与机电核对洞口位置")));
            case "ELEC" -> ps.addAll(List.of(
                    new Point("HIGH", "安全消防", "核对负荷等级、双回路/应急电源配置与防雷接地做法", "复核负荷等级与接地系统"),
                    new Point("MEDIUM", "规范符合性", "核对线路选型、桥架/管径规格与防火封堵", "核对桥架规格与防火封堵做法"),
                    new Point("MEDIUM", "安全消防", "检查应急照明、疏散指示与火灾报警联动", "补齐应急照明与报警联动说明"),
                    new Point("LOW", "专业冲突", "核对桥架路径与结构、风管、水管是否存在冲突", "与结构/暖通核对路由")));
            case "PLUMB" -> ps.addAll(List.of(
                    new Point("HIGH", "安全消防", "核对消火栓/喷淋系统布置、消防水泵房与高位水箱", "复核消防水量与泵房布置"),
                    new Point("MEDIUM", "规范符合性", "核对管径与流量、坡度与管材连接方式", "复核管径计算与连接做法"),
                    new Point("MEDIUM", "尺寸标注", "核对预留洞口尺寸、管井布置与标高", "补齐管井与预留洞标注"),
                    new Point("LOW", "专业冲突", "核对给排水管线与电气、暖通管线路由是否冲突", "与电气/暖通核对管线综合")));
            case "HVAC" -> ps.addAll(List.of(
                    new Point("MEDIUM", "规范符合性", "核对冷热源选型、系统形式、风管尺寸与保温做法", "复核系统选型与保温做法"),
                    new Point("MEDIUM", "节能环保", "核对新风量、排烟量计算与设备能效指标", "复核能耗指标与设备能效"),
                    new Point("MEDIUM", "安全消防", "核对防排烟系统设计与防火阀设置", "复核防排烟与防火阀布置"),
                    new Point("LOW", "专业冲突", "核对风管路径与结构梁、电气桥架是否冲突", "与结构/电气核对路由")));
            case "INTERIOR" -> ps.addAll(List.of(
                    new Point("MEDIUM", "图面表达", "核对装饰做法、材料样板与收口节点图是否齐全", "补齐收口节点详图"),
                    new Point("MEDIUM", "材料设备", "核对主材品牌、规格、环保等级与防火等级", "明确材料品牌与防火等级"),
                    new Point("LOW", "专业冲突", "核对精装与机电末端点位、吊顶标高是否冲突", "与机电核对末端点位")));
            case "LANDSCAPE" -> ps.addAll(List.of(
                    new Point("MEDIUM", "规范符合性", "核对竖向设计、排水组织与海绵城市指标", "复核竖向与海绵指标"),
                    new Point("MEDIUM", "材料设备", "核对铺装、苗木、小品做法与选型", "明确铺装与苗木选型"),
                    new Point("LOW", "节能环保", "核对雨水收集、节水灌溉与照明节能做法", "补充节水与节能做法")));
            default -> { /* 未知专业仅通用要点 */ }
        }
        // 阶段要点
        if ("CONSTRUCTION".equals(stage)) {
            ps.add(new Point("MEDIUM", "规范符合性", "施工图阶段：核对设计说明深度、计算书与图面是否一致", "核对计算书与图面数据一致性"));
        }
        List<DwgIssue> out = new ArrayList<>();
        for (Point p : ps) {
            DwgIssue i = new DwgIssue();
            i.setLevel(p.level); i.setCategory(p.category); i.setDescription(p.desc); i.setSuggestion(p.sug);
            out.add(i);
        }
        return out;
    }

    private String ruleSummary(DwgDrawing d, List<DwgIssue> points) {
        long high = points.stream().filter(x -> "HIGH".equals(x.getLevel())).count();
        return String.format("【规则引擎预审】%s·%s《%s》共产出 %d 条审查要点（其中严重 %d 条）。请逐条核实并处理：确认问题填写解决说明，判定不适用可标记「无需处理」；全部「严重」问题解决后方可通过审查。",
                disciplineLabel(d.getDiscipline()), stageLabel(d.getStage()), d.getTitle(), points.size(), high);
    }

    // ==================== 工具 ====================

    /** 全部未解决问题清零后，自动提示可结案（不自动通过，仍需人工确认通过） */
    private void autoCloseIfAllResolved(Long drawingId) {
        DwgDrawing d = drawingMapper.selectById(drawingId);
        if (d == null) return;
        long open = issueMapper.selectCount(new LambdaQueryWrapper<DwgIssue>()
                .eq(DwgIssue::getDrawingId, drawingId).eq(DwgIssue::getStatus, "OPEN"));
        if (open == 0 && "REVIEWING".equals(d.getStatus())) {
            d.setRemark(mergeRemark(d.getRemark(), "全部问题已处理，待审查人确认通过"));
            drawingMapper.updateById(d);
        }
    }

    private DwgDrawing require(Long id) {
        if (id == null) throw new BizException("缺少审查单ID");
        DwgDrawing d = drawingMapper.selectById(id);
        if (d == null) throw new BizException("图纸审查单不存在");
        return d;
    }

    private DwgIssue requireIssue(Long id) {
        if (id == null) throw new BizException("缺少问题ID");
        DwgIssue i = issueMapper.selectById(id);
        if (i == null) throw new BizException("审查问题不存在");
        return i;
    }

    private AiPrompt findPrompt(String code) {
        return promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptCode, code).last("LIMIT 1"));
    }

    /** 列表聚合填充：项目名称 + 问题统计（total/open/resolved） */
    private void enrich(List<DwgDrawing> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = records.stream().map(DwgDrawing::getId).toList();
        Map<Long, Map<String, Object>> counts = issueMapper.countsByDrawing(ids).stream()
                .collect(java.util.stream.Collectors.toMap(m -> toLong(m.get("drawingId")), m -> m, (a, b) -> a));
        Set<Long> pids = records.stream().map(DwgDrawing::getProjectId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (DwgDrawing d : records) {
            Map<String, Object> c = counts.get(d.getId());
            d.setTotalIssues(c == null ? 0 : toInt(c.get("total")));
            d.setOpenIssues(c == null ? 0 : toInt(c.get("open")));
            d.setResolvedIssues(c == null ? 0 : toInt(c.get("resolved")));
            if (d.getProjectId() != null) d.setProjectName(mdmLookup.selectProjectName(d.getProjectId()));
        }
    }

    private String extOf(String filename) {
        int i = filename.lastIndexOf('.');
        if (i < 0 || i == filename.length() - 1) return null;
        return filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private File resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        String safe = relativePath.replace("..", "");
        return new File(basePath, safe);
    }

    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private String mergeRemark(String old, String add) {
        if (old == null || old.isBlank()) return add;
        if (old.contains(add)) return old;
        return old + "；" + add;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private String fmtSize(Long bytes) {
        if (bytes == null) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String disciplineLabel(String code) {
        if (code == null) return "未知";
        switch (code.toUpperCase(Locale.ROOT)) {
            case "ARCH": return "建筑";
            case "STRUCT": return "结构";
            case "ELEC": return "电气";
            case "PLUMB": return "给排水";
            case "HVAC": return "暖通";
            case "INTERIOR": return "精装修";
            case "LANDSCAPE": return "景观";
            default: return code;
        }
    }

    private String stageLabel(String code) {
        if (code == null) return "未定";
        switch (code.toUpperCase(Locale.ROOT)) {
            case "SCHEME": return "方案";
            case "CONSTRUCTION": return "施工图";
            case "AS_BUILT": return "竣工图";
            default: return code;
        }
    }

    /** 问题新增/编辑表单 */
    @Data
    public static class IssueForm {
        private String level;
        private String category;
        private String location;
        private String description;
        private String suggestion;
        private String assigneeName;
        /** 图面标注点坐标（0~100 百分比），在线审图点击标注时传入 */
        private Double markerX;
        private Double markerY;
    }
}
