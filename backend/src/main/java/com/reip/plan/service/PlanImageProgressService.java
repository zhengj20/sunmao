package com.reip.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.plan.entity.PlanImageProgress;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 形象进度确认单 + 供 cost 付款模块校验 R4：
 *   进度款申请须关联已确认形象进度单，且申请部位%不得高于已确认%
 */
@Service
@RequiredArgsConstructor
public class PlanImageProgressService {

    private final PlanMappers.ImageProgressMapper imageMapper;
    private final PlanSupport support;

    // ==================== 查询 ====================

    public PageResult<PlanImageProgress> page(long current, long size, Long projectId, String building, String status) {
        LambdaQueryWrapper<PlanImageProgress> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(PlanImageProgress::getProjectId, projectId);
        if (building != null && !building.isBlank()) qw.eq(PlanImageProgress::getBuilding, building);
        if (status != null && !status.isBlank()) qw.eq(PlanImageProgress::getStatus, status);
        qw.orderByDesc(PlanImageProgress::getId);
        Page<PlanImageProgress> p = imageMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PlanImageProgress detail(Long id) {
        return require(id);
    }

    // ==================== 确认单 ====================

    @Transactional
    public Long create(PlanImageProgress i) {
        if (i == null) throw new BizException("缺少形象进度内容");
        if (i.getProjectId() == null) throw new BizException("请选择项目");
        if (i.getBuilding() == null || i.getBuilding().isBlank()) throw new BizException("请填写楼栋");
        if (i.getPartName() == null || i.getPartName().isBlank()) throw new BizException("请填写部位");
        if (i.getConfirmedPct() == null) throw new BizException("请填写完成百分比");
        i.setId(null);
        if (i.getStatus() == null) i.setStatus("PENDING");
        imageMapper.insert(i);
        return i.getId();
    }

    /** 确认：状态 CONFIRMED + 确认日期 */
    @Transactional
    public void confirm(Long id) {
        PlanImageProgress i = require(id);
        i.setStatus("CONFIRMED");
        if (i.getConfirmDate() == null) i.setConfirmDate(LocalDate.now());
        if (imageMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 供 cost 付款 R4 校验 ====================

    /**
     * R4 校验入口（供 cost 进度款申请调用）：
     *   1) 该 项目×楼栋×部位 须存在已确认形象进度单；
     *   2) 申请部位% 不得高于已确认%。
     * 违规抛 BizException。
     */
    public void validateForPayment(Long projectId, String building, String partName, BigDecimal applyPct) {
        if (projectId == null || building == null || building.isBlank() || partName == null || partName.isBlank())
            throw new BizException("进度款申请须关联形象进度(项目/楼栋/部位)");
        BigDecimal confirmed = imageMapper.maxConfirmedPct(projectId, building, partName);
        if (confirmed == null || confirmed.compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("部位[" + building + "/" + partName + "]无已确认形象进度单，不能申请进度款(R4)");
        if (applyPct != null && applyPct.compareTo(confirmed) > 0)
            throw new BizException("申请部位进度 " + applyPct + "% 高于已确认 " + confirmed + "%(R4)");
    }

    /** 查询已确认形象进度%（供 cost 侧展示/带出） */
    public BigDecimal confirmedPct(Long projectId, String building, String partName) {
        BigDecimal v = imageMapper.maxConfirmedPct(projectId, building, partName);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 某项目已确认形象进度清单（供 cost 侧选择关联单） */
    public List<PlanImageProgress> confirmedList(Long projectId, String building) {
        LambdaQueryWrapper<PlanImageProgress> qw = new LambdaQueryWrapper<>();
        qw.eq(PlanImageProgress::getProjectId, projectId)
                .eq(PlanImageProgress::getStatus, "CONFIRMED");
        if (building != null && !building.isBlank()) qw.eq(PlanImageProgress::getBuilding, building);
        qw.orderByDesc(PlanImageProgress::getId);
        return imageMapper.selectList(qw);
    }

    // ==================== 私有 ====================

    private PlanImageProgress require(Long id) {
        if (id == null) throw new BizException("缺少形象进度ID");
        PlanImageProgress i = imageMapper.selectById(id);
        if (i == null) throw new BizException("形象进度确认单不存在");
        return i;
    }
}
