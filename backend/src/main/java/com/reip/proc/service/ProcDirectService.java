package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.proc.entity.ProcDirect;
import com.reip.proc.mapper.ProcMappers.DirectMapper;
import com.reip.proc.mapper.ProcMappers.LedgerMapper;
import com.reip.proc.mapper.ProcMappers.MdmMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直接委托：CRUD+审批；阳光度统计（直接委托金额占全部定标金额比例）
 */
@Service
@RequiredArgsConstructor
public class ProcDirectService {

    private final DirectMapper directMapper;
    private final MdmMapper mdmMapper;
    private final LedgerMapper ledgerMapper;
    private final WorkflowClient workflowClient;

    public PageResult<ProcDirect> page(long current, long size, Long projectId, String status) {
        LambdaQueryWrapper<ProcDirect> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(ProcDirect::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(ProcDirect::getStatus, status);
        qw.orderByDesc(ProcDirect::getId);
        Page<ProcDirect> p = directMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcDirect detail(Long id) {
        ProcDirect d = directMapper.selectById(id);
        if (d == null) throw new BizException("直接委托记录不存在");
        return d;
    }

    @Transactional
    public Long create(ProcDirect direct) {
        if (direct.getProjectId() == null) throw new BizException("缺少项目ID");
        if (direct.getSupplierId() == null) throw new BizException("缺少供应商");
        if (direct.getAmount() == null) throw new BizException("缺少委托金额");
        Map<String, Object> supplier = mdmMapper.selectSupplier(direct.getSupplierId());
        if (supplier == null) throw new BizException("供应商不存在");
        direct.setId(null);
        direct.setSupplierName((String) supplier.get("name"));
        direct.setStatus("DRAFT");
        directMapper.insert(direct);
        return direct.getId();
    }

    @Transactional
    public void update(ProcDirect direct) {
        ProcDirect db = detail(direct.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的直接委托可修改");
        if (direct.getSupplierId() != null) {
            Map<String, Object> supplier = mdmMapper.selectSupplier(direct.getSupplierId());
            if (supplier == null) throw new BizException("供应商不存在");
            db.setSupplierId(direct.getSupplierId());
            db.setSupplierName((String) supplier.get("name"));
        }
        if (direct.getTitle() != null) db.setTitle(direct.getTitle());
        if (direct.getAmount() != null) db.setAmount(direct.getAmount());
        db.setDirectReason(direct.getDirectReason());
        db.setReasonDesc(direct.getReasonDesc());
        if (directMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ProcDirect db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的直接委托可删除");
        directMapper.deleteById(id);
    }

    /** 提交审批（PROC_DIRECT）：DRAFT→APPROVING */
    @Transactional
    public void submit(Long id) {
        ProcDirect db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的直接委托可提交审批");
        db.setStatus("APPROVING");
        if (directMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("委托事项", db.getTitle());
        snapshot.put("供应商", db.getSupplierName());
        snapshot.put("委托金额", db.getAmount());
        snapshot.put("委托原因", db.getDirectReason());
        workflowClient.start("PROC_DIRECT", id, "直接委托审批：" + db.getTitle(),
                db.getAmount(), snapshot);
    }

    /** 阳光度：直接委托金额占全部定标金额（直接委托+招标定标）比例 */
    public Map<String, Object> sunlight(Long projectId) {
        if (projectId == null) throw new BizException("缺少项目ID");
        BigDecimal directAmount = ledgerMapper.sumDirectAmount(projectId);
        BigDecimal awardAmount = ledgerMapper.sumAwardAmount(projectId);
        BigDecimal totalAmount = directAmount.add(awardAmount);
        BigDecimal directPct = totalAmount.compareTo(BigDecimal.ZERO) > 0
                ? directAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", projectId);
        m.put("directAmount", directAmount);
        m.put("awardAmount", awardAmount);
        m.put("totalAmount", totalAmount);
        m.put("directPct", directPct);
        m.put("sunlightPct", BigDecimal.valueOf(100).subtract(directPct));
        return m;
    }
}
