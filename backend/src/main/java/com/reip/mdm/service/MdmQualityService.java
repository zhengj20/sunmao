package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmCustomer;
import com.reip.mdm.entity.MdmProject;
import com.reip.mdm.entity.MdmQualityIssue;
import com.reip.mdm.entity.MdmSupplier;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主数据数据质量巡检：规则扫描落库 / 问题分页 / 处置 / 汇总报告
 */
@Service
@RequiredArgsConstructor
public class MdmQualityService {

    private final MdmMappers.SupplierMapper supplierMapper;
    private final MdmMappers.CustomerMapper customerMapper;
    private final MdmMappers.ProjectMapper projectMapper;
    private final MdmMappers.QualityIssueMapper issueMapper;

    /**
     * 执行巡检并落库。
     * 规则：
     *  1.供应商 credit_code 为空 → MISSING_VALUE
     *  2.供应商 credit_code 重复 → DUPLICATE
     *  3.客户 cert_no 重复 → DUPLICATE
     *  4.项目 saleable_area 为空或<=0 → MISSING_VALUE
     *  5.供应商 name 为空 → MISSING_VALUE
     * 去重：同 entity_type+entity_id+issue_type 已存在且 OPEN 则跳过
     */
    @Transactional
    public Map<String, Object> scan() {
        int[] counter = {0, 0}; // [0]=created [1]=skipped（lambda 内可写，避免非 effectively-final 捕获）

        // 规则 1/2/5：供应商
        List<MdmSupplier> suppliers = supplierMapper.selectList(null);
        Map<String, List<MdmSupplier>> byCredit = suppliers.stream()
                .filter(s -> s.getCreditCode() != null && !s.getCreditCode().isBlank())
                .collect(Collectors.groupingBy(MdmSupplier::getCreditCode));
        for (MdmSupplier s : suppliers) {
            if (s.getCreditCode() == null || s.getCreditCode().isBlank()) {
                int r = upsert("SUPPLIER", s.getId(), "MISSING_VALUE", "供应商统一社会信用代码为空", "WARN");
                if (r == 1) counter[0]++; else counter[1]++;
            }
            if (s.getName() == null || s.getName().isBlank()) {
                int r = upsert("SUPPLIER", s.getId(), "MISSING_VALUE", "供应商名称为空", "WARN");
                if (r == 1) counter[0]++; else counter[1]++;
            }
        }
        byCredit.forEach((code, list) -> {
            if (list.size() > 1) {
                for (MdmSupplier s : list) {
                    int r = upsert("SUPPLIER", s.getId(), "DUPLICATE",
                            "统一社会信用代码重复：" + code, "WARN");
                    if (r == 1) counter[0]++; else counter[1]++;
                }
            }
        });

        // 规则 3：客户 cert_no 重复
        List<MdmCustomer> customers = customerMapper.selectList(null);
        Map<String, List<MdmCustomer>> byCert = customers.stream()
                .filter(c -> c.getCertNo() != null && !c.getCertNo().isBlank())
                .collect(Collectors.groupingBy(MdmCustomer::getCertNo));
        byCert.forEach((cert, list) -> {
            if (list.size() > 1) {
                for (MdmCustomer c : list) {
                    int r = upsert("CUSTOMER", c.getId(), "DUPLICATE", "证件号重复：" + cert, "WARN");
                    if (r == 1) counter[0]++; else counter[1]++;
                }
            }
        });

        // 规则 4：项目 saleable_area 为空或<=0
        List<MdmProject> projects = projectMapper.selectList(null);
        for (MdmProject p : projects) {
            if (p.getSaleableArea() == null || p.getSaleableArea().compareTo(BigDecimal.ZERO) <= 0) {
                int r = upsert("PROJECT", p.getId(), "MISSING_VALUE", "可售面积为空或<=0", "WARN");
                if (r == 1) counter[0]++; else counter[1]++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", counter[0]);
        result.put("skipped", counter[1]);
        result.put("scannedAt", LocalDateTime.now());
        return result;
    }

    /** 问题分页（severity/status/entityType 过滤） */
    public PageResult<MdmQualityIssue> page(long current, long size, String severity, String status, String entityType) {
        LambdaQueryWrapper<MdmQualityIssue> qw = new LambdaQueryWrapper<>();
        if (severity != null && !severity.isBlank()) qw.eq(MdmQualityIssue::getSeverity, severity);
        if (status != null && !status.isBlank()) qw.eq(MdmQualityIssue::getStatus, status);
        if (entityType != null && !entityType.isBlank()) qw.eq(MdmQualityIssue::getEntityType, entityType);
        qw.orderByDesc(MdmQualityIssue::getId);
        Page<MdmQualityIssue> p = issueMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 处置：FIXED 已修复 / IGNORED 已忽略 */
    @Transactional
    public void resolve(Long id, String status) {
        if (!"FIXED".equals(status) && !"IGNORED".equals(status)) {
            throw new BizException("状态必须为 FIXED 或 IGNORED");
        }
        MdmQualityIssue issue = issueMapper.selectById(id);
        if (issue == null) throw new BizException("问题记录不存在");
        MdmQualityIssue up = new MdmQualityIssue();
        up.setId(id);
        up.setVersion(issue.getVersion());
        up.setStatus(status);
        int updated = issueMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 汇总报告：总数、各 severity 计数、各 entityType 计数、Top 问题类型 */
    public Map<String, Object> report() {
        List<MdmQualityIssue> all = issueMapper.selectList(null);

        Map<String, Long> bySeverity = all.stream()
                .collect(Collectors.groupingBy(MdmQualityIssue::getSeverity, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byEntityType = all.stream()
                .collect(Collectors.groupingBy(MdmQualityIssue::getEntityType, LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> topIssues = all.stream()
                .collect(Collectors.groupingBy(MdmQualityIssue::getIssueType, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("issueType", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("bySeverity", bySeverity);
        result.put("byEntityType", byEntityType);
        result.put("topIssues", topIssues);
        result.put("scannedAt", all.stream().map(MdmQualityIssue::getScannedAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
        return result;
    }

    /** 落库：同 entity_type+entity_id+issue_type 已存在且 OPEN 则跳过，返回 1=新建 0=跳过 */
    private int upsert(String entityType, Long entityId, String issueType, String detail, String severity) {
        Long cnt = issueMapper.selectCount(new LambdaQueryWrapper<MdmQualityIssue>()
                .eq(MdmQualityIssue::getEntityType, entityType)
                .eq(MdmQualityIssue::getEntityId, entityId)
                .eq(MdmQualityIssue::getIssueType, issueType)
                .eq(MdmQualityIssue::getStatus, "OPEN"));
        if (cnt != null && cnt > 0) return 0;
        MdmQualityIssue issue = new MdmQualityIssue();
        issue.setEntityType(entityType);
        issue.setEntityId(entityId);
        issue.setIssueType(issueType);
        issue.setDetail(detail);
        issue.setSeverity(severity);
        issue.setStatus("OPEN");
        issue.setScannedAt(LocalDateTime.now());
        issueMapper.insert(issue);
        return 1;
    }
}
