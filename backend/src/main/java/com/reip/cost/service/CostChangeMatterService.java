package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostChange;
import com.reip.cost.entity.CostChangeMatter;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变更事项申请：签证/设计变更的上游"申请/背景"。
 * 1 事项 → N 变更（通过 cost_change.matter_id 关联），并聚合预估/已确认金额。
 */
@Service
@RequiredArgsConstructor
public class CostChangeMatterService {

    private final CostMappers.MatterMapper matterMapper;
    private final CostMappers.ChangeMapper changeMapper;
    private final CodeRuleService codeRuleService;
    private final CostSupport support;

    // ==================== 查询 ====================

    public PageResult<CostChangeMatter> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<CostChangeMatter> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostChangeMatter::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(CostChangeMatter::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(CostChangeMatter::getTitle, kw).or().like(CostChangeMatter::getMatterCode, kw));
        qw.orderByDesc(CostChangeMatter::getId);
        Page<CostChangeMatter> p = matterMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostChangeMatter detail(Long id) {
        CostChangeMatter m = matterMapper.selectById(id);
        if (m == null) throw new BizException("事项申请不存在");
        return m;
    }

    /** 事项 + 关联变更 + 金额汇总（前端详情页用） */
    public Map<String, Object> summary(Long id) {
        CostChangeMatter m = detail(id);
        List<CostChange> changes = changeMapper.selectList(
                new LambdaQueryWrapper<CostChange>().eq(CostChange::getMatterId, id).orderByDesc(CostChange::getId));
        BigDecimal estimateSum = BigDecimal.ZERO;
        BigDecimal confirmedSum = BigDecimal.ZERO;
        for (CostChange c : changes) {
            if (c.getEstimateAmount() != null) estimateSum = estimateSum.add(c.getEstimateAmount());
            if ("CONFIRMED".equals(c.getStatus())) {
                BigDecimal net = CostSupport.nvl(c.getConfirmedAmount()).subtract(CostSupport.nvl(c.getDeductAmount()));
                confirmedSum = confirmedSum.add(net);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("matter", m);
        r.put("changes", changes);
        r.put("changeCount", changes.size());
        r.put("estimateSum", estimateSum);
        r.put("confirmedSum", confirmedSum);
        return r;
    }

    /** 事项下的变更列表（用于"新增变更"预填与下钻） */
    public List<CostChange> linkedChanges(Long matterId) {
        return changeMapper.selectList(
                new LambdaQueryWrapper<CostChange>().eq(CostChange::getMatterId, matterId).orderByDesc(CostChange::getId));
    }

    // ==================== CRUD ====================

    @Transactional
    public Long create(CostChangeMatter m) {
        if (m == null) throw new BizException("缺少表单内容");
        if (m.getTitle() == null || m.getTitle().isBlank()) throw new BizException("请填写事项名称");
        if (m.getProjectId() == null) throw new BizException("请选择项目");
        m.setId(null);
        m.setMatterCode(codeRuleService.next("SM", support.codeCtx(m.getProjectId())));
        if (m.getStatus() == null || m.getStatus().isBlank()) m.setStatus("DRAFT");
        if (m.getEstimateTotal() == null) m.setEstimateTotal(BigDecimal.ZERO);
        matterMapper.insert(m);
        return m.getId();
    }

    @Transactional
    public void update(CostChangeMatter m) {
        if (m == null || m.getId() == null) throw new BizException("缺少事项ID");
        CostChangeMatter old = detail(m.getId());
        if (!"DRAFT".equals(old.getStatus())) throw new BizException("已批准/已关闭事项不可修改，请关闭后重建");
        if (m.getTitle() != null && m.getTitle().isBlank()) throw new BizException("事项名称不能为空");
        m.setMatterCode(old.getMatterCode()); // 编号不可改
        m.setStatus(old.getStatus());
        matterMapper.updateById(m);
    }

    @Transactional
    public void delete(Long id) {
        CostChangeMatter m = detail(id);
        if (!"DRAFT".equals(m.getStatus())) throw new BizException("已批准事项不可删除");
        Long count = changeMapper.selectCount(new LambdaQueryWrapper<CostChange>().eq(CostChange::getMatterId, id));
        if (count != null && count > 0) throw new BizException("该事项下已有" + count + "笔变更，请先移除关联变更");
        matterMapper.deleteById(id);
    }

    /** 批准事项（申请→已批准） */
    @Transactional
    public void approve(Long id) {
        CostChangeMatter m = detail(id);
        if (!"DRAFT".equals(m.getStatus())) throw new BizException("当前状态不可批准");
        CostChangeMatter up = new CostChangeMatter();
        up.setId(id);
        up.setVersion(m.getVersion());
        up.setStatus("APPROVED");
        matterMapper.updateById(up);
    }

    /** 关闭事项 */
    @Transactional
    public void close(Long id) {
        CostChangeMatter m = detail(id);
        if ("CLOSED".equals(m.getStatus())) throw new BizException("事项已关闭");
        CostChangeMatter up = new CostChangeMatter();
        up.setId(id);
        up.setVersion(m.getVersion());
        up.setStatus("CLOSED");
        matterMapper.updateById(up);
    }
}
