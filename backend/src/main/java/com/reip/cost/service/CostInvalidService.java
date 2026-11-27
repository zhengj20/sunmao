package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostDynamicSnapshot;
import com.reip.cost.entity.CostInvalid;
import com.reip.cost.mapper.CostMappers;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无效成本认定：CRUD / 提交认定 / 无效成本占比
 */
@Service
@RequiredArgsConstructor
public class CostInvalidService {

    private final CostMappers.InvalidMapper invalidMapper;
    private final CostMappers.DynamicSnapshotMapper snapshotMapper;
    private final WorkflowClient workflowClient;

    public PageResult<CostInvalid> page(long current, long size, Long projectId, String status, String kw) {
        LambdaQueryWrapper<CostInvalid> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostInvalid::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(CostInvalid::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(CostInvalid::getInvalidType, kw)
                .or().like(CostInvalid::getDescription, kw));
        qw.orderByDesc(CostInvalid::getId);
        Page<CostInvalid> p = invalidMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostInvalid detail(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(CostInvalid invalid) {
        if (invalid == null) throw new BizException("缺少表单内容");
        if (invalid.getProjectId() == null) throw new BizException("请选择项目");
        if (invalid.getAmount() == null) throw new BizException("请填写认定金额");
        invalid.setId(null);
        invalid.setStatus("DRAFT");
        invalidMapper.insert(invalid);
        return invalid.getId();
    }

    @Transactional
    public void update(CostInvalid invalid) {
        if (invalid == null || invalid.getId() == null) throw new BizException("缺少认定单ID");
        CostInvalid db = require(invalid.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的认定单可修改");
        invalid.setStatus(db.getStatus());
        if (invalidMapper.updateById(invalid) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostInvalid db = require(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的认定单可删除");
        invalidMapper.deleteById(id);
    }

    /** 提交认定审批（bizType=COST_INVALID） */
    @Transactional
    public void submit(Long id) {
        CostInvalid db = require(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的认定单可提交审批");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("invalidType", db.getInvalidType());
        snapshot.put("amount", db.getAmount());
        snapshot.put("occurredDate", db.getOccurredDate());
        snapshot.put("projectId", db.getProjectId());
        workflowClient.start("COST_INVALID", id, "无效成本认定 #" + id, db.getAmount(), snapshot);
        CostInvalid up = new CostInvalid();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("APPROVING");
        if (invalidMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 工作流回调：通过 → CONFIRMED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostInvalid db = invalidMapper.selectById(id);
        if (db == null || !"APPROVING".equals(db.getStatus())) return; // 幂等
        CostInvalid up = new CostInvalid();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("CONFIRMED");
        invalidMapper.updateById(up);
    }

    /** 工作流回调：驳回 → DRAFT */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostInvalid db = invalidMapper.selectById(id);
        if (db == null || !"APPROVING".equals(db.getStatus())) return;
        CostInvalid up = new CostInvalid();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("DRAFT");
        invalidMapper.updateById(up);
    }

    /** 无效成本占比 = 已确认无效成本合计 / 最新动态成本合计 */
    public Map<String, Object> rate(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        BigDecimal invalidTotal = invalidMapper.selectList(new LambdaQueryWrapper<CostInvalid>()
                        .eq(CostInvalid::getProjectId, projectId).eq(CostInvalid::getStatus, "CONFIRMED")).stream()
                .map(i -> CostSupport.nvl(i.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CostDynamicSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .orderByDesc(CostDynamicSnapshot::getSnapMonth).last("LIMIT 1"));
        BigDecimal dynamicTotal = latest == null ? BigDecimal.ZERO : CostSupport.nvl(latest.getDynamicTotal());
        BigDecimal rate = dynamicTotal.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : invalidTotal.multiply(BigDecimal.valueOf(100)).divide(dynamicTotal, 2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("invalidTotal", invalidTotal);
        result.put("dynamicTotal", dynamicTotal);
        result.put("rate", rate);
        return result;
    }

    private CostInvalid require(Long id) {
        if (id == null) throw new BizException("缺少认定单ID");
        CostInvalid db = invalidMapper.selectById(id);
        if (db == null) throw new BizException("无效成本认定单不存在");
        return db;
    }
}
