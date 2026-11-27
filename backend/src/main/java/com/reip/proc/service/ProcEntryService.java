package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.framework.param.ParamService;
import com.reip.proc.entity.ProcEntry;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.mapper.ProcMappers.EntryMapper;
import com.reip.proc.mapper.ProcMappers.MdmMapper;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 入围与考察：黑名单硬校验、入围家数校验、入围名单整体审批
 */
@Service
@RequiredArgsConstructor
public class ProcEntryService {

    private final EntryMapper entryMapper;
    private final ProjectMapper projectMapper;
    private final MdmMapper mdmMapper;
    private final ParamService paramService;
    private final WorkflowClient workflowClient;

    /** 按招采立项维度列出入围名单 */
    public List<ProcEntry> list(Long procProjectId) {
        return entryMapper.selectList(new LambdaQueryWrapper<ProcEntry>()
                .eq(ProcEntry::getProcProjectId, procProjectId)
                .orderByDesc(ProcEntry::getIsCandidate)
                .orderByAsc(ProcEntry::getId));
    }

    @Transactional
    public Long create(ProcEntry entry) {
        if (entry.getProcProjectId() == null) throw new BizException("缺少招采立项ID");
        if (entry.getSupplierId() == null) throw new BizException("缺少供应商");
        ProcProject project = projectMapper.selectById(entry.getProcProjectId());
        if (project == null) throw new BizException("招采立项不存在");
        Map<String, Object> supplier = mdmMapper.selectSupplier(entry.getSupplierId());
        if (supplier == null) throw new BizException("供应商不存在");
        // 阳光采购红线：黑名单供应商不能入围
        if ("BLACKLIST".equals(supplier.get("libType"))) throw new BizException("黑名单供应商不能入围");
        Long cnt = entryMapper.selectCount(new LambdaQueryWrapper<ProcEntry>()
                .eq(ProcEntry::getProcProjectId, entry.getProcProjectId())
                .eq(ProcEntry::getSupplierId, entry.getSupplierId()));
        if (cnt != null && cnt > 0) throw new BizException("该供应商已入围，请勿重复添加");
        entry.setSupplierName((String) supplier.get("name"));
        entry.setStatus("PENDING");
        if (entry.getIsCandidate() == null) entry.setIsCandidate(1);
        entryMapper.insert(entry);
        return entry.getId();
    }

    /** 资格预审结果/考察评分/结论/备选标记更新 */
    @Transactional
    public void update(ProcEntry entry) {
        ProcEntry db = entryMapper.selectById(entry.getId());
        if (db == null) throw new BizException("入围记录不存在");
        if (!"PENDING".equals(db.getStatus())) throw new BizException("仅待提交状态的入围记录可修改");
        db.setPrecheckResult(entry.getPrecheckResult());
        db.setPrecheckRemark(entry.getPrecheckRemark());
        db.setInspectionScore(entry.getInspectionScore());
        db.setInspectionConclusion(entry.getInspectionConclusion());
        if (entry.getIsCandidate() != null) db.setIsCandidate(entry.getIsCandidate());
        if (entryMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcEntry db = entryMapper.selectById(id);
        if (db == null) throw new BizException("入围记录不存在");
        if (!"PENDING".equals(db.getStatus())) throw new BizException("仅待提交状态的入围记录可删除");
        entryMapper.deleteById(id);
    }

    /**
     * 提交入围名单审批（PROC_ENTRY，bizId=procProjectId，金额=立项预算，摘要含供应商名单）。
     * 阳光采购规则：正式入围家数不得少于 proc.award.min_entry_count（默认3家）。
     */
    @Transactional
    public void submit(Long procProjectId) {
        ProcProject project = projectMapper.selectById(procProjectId);
        if (project == null) throw new BizException("招采立项不存在");
        if (!"APPROVED".equals(project.getStatus())) throw new BizException("立项审批通过后才能提交入围名单");
        List<ProcEntry> entries = list(procProjectId);
        if (entries.isEmpty()) throw new BizException("请先添加入围供应商");
        int minCount = paramService.getInt("proc.award.min_entry_count", 3);
        long formalCount = entries.stream()
                .filter(e -> e.getIsCandidate() != null && e.getIsCandidate() == 1).count();
        if (formalCount < minCount) throw new BizException("入围家数不足" + minCount + "家");
        for (ProcEntry e : entries) {
            e.setStatus("APPROVING");
            if (entryMapper.updateById(e) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("立项编号", project.getProjectCode());
        snapshot.put("标的名称", project.getTitle());
        snapshot.put("正式入围家数", formalCount);
        snapshot.put("入围名单", entries.stream()
                .filter(e -> e.getIsCandidate() != null && e.getIsCandidate() == 1)
                .map(ProcEntry::getSupplierName).collect(Collectors.joining("、")));
        workflowClient.start("PROC_ENTRY", procProjectId, "入围名单审批：" + project.getTitle(),
                project.getBudgetAmount(), snapshot);
    }
}
