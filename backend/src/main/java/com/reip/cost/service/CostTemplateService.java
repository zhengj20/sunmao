package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostContractTemplate;
import com.reip.cost.mapper.CostMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合同模板：上传 / 列表 / 按模板+合同数据生成 .docx（占位符替换）/ 示例模板引导
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostTemplateService {

    private final CostMappers.TemplateMapper templateMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CostMappers.MdmLookupMapper mdmLookup;

    @Value("${reip.upload.base-path:./uploads}")
    private String basePath;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public PageResult<CostContractTemplate> page(long current, long size, String contractType, String kw) {
        LambdaQueryWrapper<CostContractTemplate> qw = new LambdaQueryWrapper<>();
        if (contractType != null && !contractType.isBlank()) qw.eq(CostContractTemplate::getContractType, contractType);
        if (kw != null && !kw.isBlank()) qw.like(CostContractTemplate::getName, kw);
        qw.orderByDesc(CostContractTemplate::getId);
        Page<CostContractTemplate> p = templateMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostContractTemplate detail(Long id) {
        CostContractTemplate t = templateMapper.selectById(id);
        if (t == null) throw new BizException("合同模板不存在");
        return t;
    }

    @Transactional
    public void delete(Long id) {
        CostContractTemplate t = detail(id);
        File f = resolve(t.getFilePath());
        if (f != null && f.exists()) f.delete();
        templateMapper.deleteById(id);
    }

    /** 上传 .docx 模板 */
    public CostContractTemplate upload(MultipartFile file, String name, String contractType, String description) {
        if (file == null || file.isEmpty()) throw new BizException("请选择要上传的 .docx 合同模板文件");
        String orig = file.getOriginalFilename() == null ? "template.docx" : file.getOriginalFilename();
        if (!orig.toLowerCase().endsWith(".docx")) throw new BizException("仅支持 .docx 格式的 Word 模板");
        if (name == null || name.isBlank()) name = orig;
        String rel = "templates/" + UUID.randomUUID() + "_" + orig;
        File dir = new File(basePath, "templates");
        if (!dir.exists()) dir.mkdirs();
        try {
            file.transferTo(new File(basePath, rel).getAbsoluteFile());
        } catch (Exception e) {
            throw new BizException("模板文件保存失败：" + e.getMessage());
        }
        CostContractTemplate t = new CostContractTemplate();
        t.setName(name.trim());
        t.setContractType(blankToNull(contractType));
        t.setFileName(orig);
        t.setFilePath(rel);
        t.setFileSize(file.getSize());
        t.setDescription(blankToNull(description));
        t.setStatus("ACTIVE");
        t.setPlaceholders(defaultPlaceholdersJson());
        templateMapper.insert(t);
        return t;
    }

    /** 按模板 + 合同数据生成 .docx，返回文件地址与文件名 */
    public Map<String, Object> generate(Long templateId, Long contractId) {
        CostContractTemplate tpl = detail(templateId);
        CostContract c = contractMapper.selectById(contractId);
        if (c == null) throw new BizException("合同不存在");
        File src = resolve(tpl.getFilePath());
        if (src == null || !src.exists()) throw new BizException("模板文件不存在，请重新上传");

        Map<String, String> map = buildPlaceholders(c);
        String rel = "generated/合同_" + c.getContractCode() + "_" + LocalDateTime.now().format(TS) + ".docx";
        File out = new File(basePath, rel);
        if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(src);
             XWPFDocument doc = new XWPFDocument(in);
             FileOutputStream outStream = new FileOutputStream(out)) {
            fill(doc, map);
            doc.write(outStream);
        } catch (Exception e) {
            log.error("生成合同失败", e);
            throw new BizException("生成合同失败：" + e.getMessage());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("templateId", tpl.getId());
        r.put("contractId", c.getId());
        r.put("contractCode", c.getContractCode());
        r.put("fileName", out.getName());
        r.put("fileUrl", "/files/" + rel);
        r.put("size", out.length());
        r.put("placeholders", map);
        return r;
    }

    /** 启动时若没有任何模板，则生成一份示例模板（文件 + 记录）便于演示 */
    @Transactional
    public void initSample() {
        Long count = templateMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) return;
        String rel = "templates/示例_施工总承包合同模板.docx";
        File f = new File(basePath, rel);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            buildSampleDoc().write(fos);
        } catch (Exception e) {
            log.warn("生成示例合同模板失败（不影响使用）: {}", e.getMessage());
            return;
        }
        CostContractTemplate t = new CostContractTemplate();
        t.setName("施工总承包合同（示例模板）");
        t.setContractType("GC总包");
        t.setFileName(f.getName());
        t.setFilePath(rel);
        t.setFileSize(f.length());
        t.setDescription("系统示例模板：含 {{合同编号}}、{{承包商}}、{{签约金额}} 等占位符，可预览或据此生成合同");
        t.setStatus("ACTIVE");
        t.setPlaceholders(defaultPlaceholdersJson());
        templateMapper.insert(t);
        log.info("已生成示例合同模板: {}", rel);
    }

    // ---------- POI 占位符替换 ----------

    private void fill(XWPFDocument doc, Map<String, String> map) {
        for (XWPFParagraph p : doc.getParagraphs()) replaceInParagraph(p, map);
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows())
                for (XWPFTableCell cell : row.getTableCells())
                    for (XWPFParagraph p : cell.getParagraphs()) replaceInParagraph(p, map);
        }
        // 嵌套表格
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows())
                for (XWPFTableCell cell : row.getTableCells())
                    for (XWPFTable sub : cell.getTables())
                        for (XWPFTableRow srow : sub.getRows())
                            for (XWPFTableCell scell : srow.getTableCells())
                                for (XWPFParagraph p : scell.getParagraphs()) replaceInParagraph(p, map);
        }
    }

    /** 段落级替换：合并 runs 文本替换后写回首个 run（兼容占位符被 Word 拆分成多 run 的情况） */
    private void replaceInParagraph(XWPFParagraph p, Map<String, String> map) {
        List<XWPFRun> runs = p.getRuns();
        if (runs == null || runs.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.getText(0);
            if (t != null) sb.append(t);
        }
        String full = sb.toString();
        if (!full.contains("{{")) return;
        String replaced = full;
        for (Map.Entry<String, String> e : map.entrySet()) {
            replaced = replaced.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        if (replaced.equals(full)) return;
        runs.get(0).setText(replaced, 0);
        for (int i = 1; i < runs.size(); i++) runs.get(i).setText("", 0);
    }

    /** 合同字段 → 占位符值 */
    private Map<String, String> buildPlaceholders(CostContract c) {
        Map<String, String> m = new LinkedHashMap<>();
        String projectName = null;
        if (c.getProjectId() != null) projectName = c.getProjectId().toString();
        m.put("合同编号", c.getContractCode());
        m.put("合同名称", nz(c.getName()));
        m.put("承包商", nz(c.getSupplierName()));
        m.put("合同类型", nz(c.getContractType()));
        m.put("签约金额", c.getSignedAmount() == null ? "" : c.getSignedAmount().toPlainString() + " 元");
        m.put("签约日期", c.getSignedDate() == null ? "" : c.getSignedDate().toString());
        m.put("承包范围", nz(c.getScope()));
        m.put("开工日期", c.getStartDate() == null ? "" : c.getStartDate().toString());
        m.put("竣工日期", c.getEndDate() == null ? "" : c.getEndDate().toString());
        m.put("质保金比例", c.getRetentionRate() == null ? "" : c.getRetentionRate().toPlainString() + "%");
        // 英文键兜底（便于用户模板用英文占位符）
        m.put("contractCode", c.getContractCode());
        m.put("name", nz(c.getName()));
        m.put("supplierName", nz(c.getSupplierName()));
        m.put("contractType", nz(c.getContractType()));
        m.put("signedAmount", c.getSignedAmount() == null ? "" : c.getSignedAmount().toPlainString());
        m.put("signedDate", c.getSignedDate() == null ? "" : c.getSignedDate().toString());
        m.put("scope", nz(c.getScope()));
        m.put("startDate", c.getStartDate() == null ? "" : c.getStartDate().toString());
        m.put("endDate", c.getEndDate() == null ? "" : c.getEndDate().toString());
        m.put("retentionRate", c.getRetentionRate() == null ? "" : c.getRetentionRate().toPlainString());
        m.put("projectId", projectName == null ? "" : projectName);
        return m;
    }

    private XWPFDocument buildSampleDoc() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph title = doc.createParagraph();
        XWPFRun trun = title.createRun();
        trun.setText("施工总承包合同");
        trun.setBold(true);
        trun.setFontSize(20);

        doc.createParagraph(); // 空行
        line(doc, "合同编号：{{合同编号}}");
        line(doc, "合同名称：{{合同名称}}");
        line(doc, "发包方（甲方）：星汇湾置业发展有限公司");
        line(doc, "承包方（乙方/承包商）：{{承包商}}");
        line(doc, "签约日期：{{签约日期}}");
        doc.createParagraph();
        line(doc, "第一条 工程概况");
        line(doc, "承包范围：{{承包范围}}");
        line(doc, "第二条 合同价款");
        line(doc, "本合同签约金额为人民币 {{签约金额}}。");
        line(doc, "第三条 工期");
        line(doc, "开工日期 {{开工日期}}，竣工日期 {{竣工日期}}。");
        line(doc, "第四条 质保金");
        line(doc, "质保金比例为 {{质保金比例}}，工程竣工验收合格并按约定结算后予以释放。");
        line(doc, "第五条 其他");
        line(doc, "本合同未尽事宜，双方另行签订补充协议。本合同自双方签章之日起生效。");
        doc.createParagraph();
        line(doc, "甲方（签章）：________________        乙方（签章）：________________");
        line(doc, "日期：{{签约日期}}");
        return doc;
    }

    private void line(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(11);
    }

    // ---------- 工具 ----------

    private File resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        // 防止路径穿越
        String safe = relativePath.replace("..", "");
        return new File(basePath, safe);
    }

    private String nz(String s) { return s == null ? "" : s; }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private String defaultPlaceholdersJson() {
        return "[\"合同编号\",\"合同名称\",\"承包商\",\"合同类型\",\"签约金额\",\"签约日期\",\"承包范围\",\"开工日期\",\"竣工日期\",\"质保金比例\"]";
    }
}
