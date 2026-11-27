package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.param.ParamService;
import com.reip.proc.entity.ProcMisc;
import com.reip.proc.entity.ProcMiscCompare;
import com.reip.proc.mapper.ProcMappers.MiscCompareMapper;
import com.reip.proc.mapper.ProcMappers.MiscMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零星采购：CRUD + 比价子表 + 提交审批（硬校验：比价家数≥阈值且各家报价一致）
 */
@Service
@RequiredArgsConstructor
public class ProcMiscService {

    private final MiscMapper miscMapper;
    private final MiscCompareMapper miscCompareMapper;
    private final ParamService paramService;
    private final WorkflowClient workflowClient;

    public PageResult<ProcMisc> page(long current, long size, Long projectId, String status) {
        LambdaQueryWrapper<ProcMisc> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(ProcMisc::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(ProcMisc::getStatus, status);
        qw.orderByDesc(ProcMisc::getId);
        Page<ProcMisc> p = miscMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcMisc detail(Long id) {
        ProcMisc m = miscMapper.selectById(id);
        if (m == null) throw new BizException("零星采购记录不存在");
        return m;
    }

    @Transactional
    public Long create(ProcMisc misc) {
        if (misc.getProjectId() == null) throw new BizException("缺少项目ID");
        if (misc.getTitle() == null || misc.getTitle().isBlank()) throw new BizException("缺少采购事项");
        if (misc.getAmount() == null) throw new BizException("缺少采购金额");
        misc.setId(null);
        misc.setStatus("DRAFT");
        miscMapper.insert(misc);
        return misc.getId();
    }

    @Transactional
    public void update(ProcMisc misc) {
        ProcMisc db = detail(misc.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的零星采购可修改");
        if (misc.getProjectId() != null) db.setProjectId(misc.getProjectId());
        if (misc.getTitle() != null) db.setTitle(misc.getTitle());
        if (misc.getAmount() != null) db.setAmount(misc.getAmount());
        db.setReason(misc.getReason());
        db.setCompareAttachment(misc.getCompareAttachment());
        if (miscMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcMisc db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的零星采购可删除");
        miscCompareMapper.delete(new LambdaQueryWrapper<ProcMiscCompare>().eq(ProcMiscCompare::getMiscId, id));
        miscMapper.deleteById(id);
    }

    // ================= 比价子表 =================
    public List<ProcMiscCompare> listCompares(Long miscId) {
        detail(miscId);
        return miscCompareMapper.selectList(new LambdaQueryWrapper<ProcMiscCompare>()
                .eq(ProcMiscCompare::getMiscId, miscId).orderByAsc(ProcMiscCompare::getId));
    }

    @Transactional
    public Long addCompare(Long miscId, ProcMiscCompare compare) {
        detail(miscId);
        if (compare.getSupplierName() == null || compare.getSupplierName().isBlank()) throw new BizException("缺少供应商名称");
        if (compare.getPrice() == null) throw new BizException("缺少报价金额");
        compare.setId(null);
        compare.setMiscId(miscId);
        miscCompareMapper.insert(compare);
        return compare.getId();
    }

    @Transactional
    public void deleteCompare(Long miscId, Long compareId) {
        detail(miscId);
        ProcMiscCompare db = miscCompareMapper.selectById(compareId);
        if (db == null) throw new BizException("比价明细不存在");
        if (!miscId.equals(db.getMiscId())) throw new BizException("比价明细不属于该零星采购");
        miscCompareMapper.deleteById(compareId);
    }

    /** 提交审批（PROC_MISC）：硬校验比价家数≥阈值且各家报价一致，DRAFT→APPROVING */
    @Transactional
    public void submit(Long id) {
        ProcMisc db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的零星采购可提交审批");
        List<ProcMiscCompare> compares = listCompares(id);
        int min = paramService.getInt("proc.misc.min_compare", 3);
        if (compares.size() < min) throw new BizException("比价家数不足" + min + "家，不能提交");
        BigDecimal first = compares.get(0).getPrice();
        if (first == null) throw new BizException("比价金额不能为空");
        for (ProcMiscCompare c : compares) {
            if (c.getPrice() == null || first.compareTo(c.getPrice()) != 0) {
                throw new BizException("各供应商报价不一致，不能提交");
            }
        }
        db.setStatus("APPROVING");
        if (miscMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("采购事项", db.getTitle());
        snapshot.put("采购金额", db.getAmount());
        snapshot.put("比价家数", compares.size());
        snapshot.put("事由", db.getReason());
        workflowClient.start("PROC_MISC", id, "零星采购审批：" + db.getTitle(), db.getAmount(), snapshot);
    }
}
