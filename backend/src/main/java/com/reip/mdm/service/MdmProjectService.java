package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.mdm.entity.*;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 项目主数据服务：项目 CRUD、状态机、分期/业态/楼栋子资源
 */
@Service
@RequiredArgsConstructor
public class MdmProjectService {

    private final MdmMappers.ProjectMapper projectMapper;
    private final MdmMappers.PhaseMapper phaseMapper;
    private final MdmMappers.ProductTypeMapper productTypeMapper;
    private final MdmMappers.BuildingMapper buildingMapper;
    private final MdmMappers.RoomMapper roomMapper;
    private final CodeRuleService codeRuleService;
    private final MdmDistributeLogService distributeLogService;

    /** 项目状态机：PREPARING→CONSTRUCTION→DELIVERED→LIQUIDATION */
    private static final Map<String, String> NEXT_STATUS = Map.of(
            "PREPARING", "CONSTRUCTION",
            "CONSTRUCTION", "DELIVERED",
            "DELIVERED", "LIQUIDATION");

    // ================= 项目 =================

    public PageResult<MdmProject> page(long current, long size, String kw, String status) {
        LambdaQueryWrapper<MdmProject> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(MdmProject::getName, kw)
                .or().like(MdmProject::getProjectCode, kw).or().like(MdmProject::getCity, kw));
        if (status != null && !status.isBlank()) qw.eq(MdmProject::getStatus, status);
        qw.orderByDesc(MdmProject::getId);
        Page<MdmProject> p = projectMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public MdmProject detail(Long id) {
        MdmProject p = projectMapper.selectById(id);
        if (p == null) throw new BizException("项目不存在");
        return p;
    }

    /** 项目下拉选项：id/projectCode/name/status */
    public List<MdmProject> options() {
        return projectMapper.selectList(new LambdaQueryWrapper<MdmProject>()
                .select(MdmProject::getId, MdmProject::getProjectCode, MdmProject::getName, MdmProject::getStatus)
                .orderByAsc(MdmProject::getId));
    }

    /** 新增：project_code 用编码规则 PRJ 自动生成（全局唯一） */
    @Transactional
    public Long create(MdmProject project) {
        if (project.getName() == null || project.getName().isBlank()) throw new BizException("项目名称不能为空");
        project.setId(null);
        project.setProjectCode(codeRuleService.next("PRJ", null));
        if (project.getStatus() == null || project.getStatus().isBlank()) project.setStatus("PREPARING");
        projectMapper.insert(project);
        return project.getId();
    }

    @Transactional
    public void update(MdmProject project) {
        if (project.getId() == null) throw new BizException("缺少项目ID");
        if (project.getName() == null || project.getName().isBlank()) throw new BizException("项目名称不能为空");
        project.setProjectCode(null); // 项目编号不可修改
        int updated = projectMapper.updateById(project);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        distributeLogService.record("project", project.getId());
    }

    /** 状态迁移：严格校验合法流转，非法跳转拒绝 */
    @Transactional
    public void changeStatus(Long id, String status) {
        MdmProject p = projectMapper.selectById(id);
        if (p == null) throw new BizException("项目不存在");
        String next = NEXT_STATUS.get(p.getStatus());
        if (next == null || !next.equals(status)) {
            throw new BizException("非法的状态迁移：" + p.getStatus() + "→" + status);
        }
        MdmProject up = new MdmProject();
        up.setId(id);
        up.setVersion(p.getVersion());
        up.setStatus(status);
        int updated = projectMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        distributeLogService.record("project", id);
    }

    @Transactional
    public void delete(Long id) {
        projectMapper.deleteById(id);
    }

    // ================= 分期 =================

    public List<MdmPhase> phaseList(Long projectId) {
        return phaseMapper.selectList(new LambdaQueryWrapper<MdmPhase>()
                .eq(MdmPhase::getProjectId, projectId).orderByAsc(MdmPhase::getId));
    }

    @Transactional
    public Long createPhase(Long projectId, MdmPhase phase) {
        requireProject(projectId);
        if (phase.getPhaseCode() == null || phase.getPhaseCode().isBlank()) throw new BizException("分期编码不能为空");
        if (phase.getName() == null || phase.getName().isBlank()) throw new BizException("分期名称不能为空");
        phase.setId(null);
        phase.setProjectId(projectId);
        if (phase.getStatus() == null || phase.getStatus().isBlank()) phase.setStatus("PREPARING");
        phaseMapper.insert(phase);
        return phase.getId();
    }

    @Transactional
    public void updatePhase(Long projectId, Long phaseId, MdmPhase phase) {
        MdmPhase old = requirePhase(projectId, phaseId);
        phase.setId(phaseId);
        phase.setProjectId(null);
        phase.setVersion(old.getVersion());
        int updated = phaseMapper.updateById(phase);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deletePhase(Long projectId, Long phaseId) {
        requirePhase(projectId, phaseId);
        phaseMapper.deleteById(phaseId);
    }

    private MdmPhase requirePhase(Long projectId, Long phaseId) {
        MdmPhase phase = phaseMapper.selectById(phaseId);
        if (phase == null || !phase.getProjectId().equals(projectId)) throw new BizException("分期不存在");
        return phase;
    }

    // ================= 业态 =================

    public List<MdmProductType> productTypeList(Long projectId) {
        return productTypeMapper.selectList(new LambdaQueryWrapper<MdmProductType>()
                .eq(MdmProductType::getProjectId, projectId)
                .orderByAsc(MdmProductType::getSort).orderByAsc(MdmProductType::getId));
    }

    @Transactional
    public Long createProductType(Long projectId, MdmProductType productType) {
        requireProject(projectId);
        if (productType.getName() == null || productType.getName().isBlank()) throw new BizException("业态名称不能为空");
        productType.setId(null);
        productType.setProjectId(projectId);
        if (productType.getSaleable() == null) productType.setSaleable(1);
        productTypeMapper.insert(productType);
        return productType.getId();
    }

    @Transactional
    public void updateProductType(Long projectId, Long productTypeId, MdmProductType productType) {
        MdmProductType old = requireProductType(projectId, productTypeId);
        productType.setId(productTypeId);
        productType.setProjectId(null);
        productType.setVersion(old.getVersion());
        int updated = productTypeMapper.updateById(productType);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteProductType(Long projectId, Long productTypeId) {
        requireProductType(projectId, productTypeId);
        productTypeMapper.deleteById(productTypeId);
    }

    private MdmProductType requireProductType(Long projectId, Long productTypeId) {
        MdmProductType pt = productTypeMapper.selectById(productTypeId);
        if (pt == null || !pt.getProjectId().equals(projectId)) throw new BizException("业态不存在");
        return pt;
    }

    // ================= 楼栋 =================

    /** 楼栋列表（projectId 为空时返回全部） */
    public List<MdmBuilding> buildingList(Long projectId) {
        LambdaQueryWrapper<MdmBuilding> qw = new LambdaQueryWrapper<MdmBuilding>()
                .eq(projectId != null, MdmBuilding::getProjectId, projectId)
                .orderByAsc(MdmBuilding::getId);
        return buildingMapper.selectList(qw);
    }

    @Transactional
    public Long createBuilding(Long projectId, MdmBuilding building) {
        requireProject(projectId);
        if (building.getBuildingCode() == null || building.getBuildingCode().isBlank()) throw new BizException("栋号不能为空");
        building.setId(null);
        building.setProjectId(projectId);
        if (building.getStatus() == null || building.getStatus().isBlank()) building.setStatus("PLANNED");
        if (building.getUndergroundFloors() == null) building.setUndergroundFloors(0);
        if (building.getUnits() == null) building.setUnits(1);
        buildingMapper.insert(building);
        return building.getId();
    }

    @Transactional
    public void updateBuilding(Long projectId, Long buildingId, MdmBuilding building) {
        MdmBuilding old = requireBuilding(projectId, buildingId);
        building.setId(buildingId);
        building.setProjectId(null);
        building.setVersion(old.getVersion());
        int updated = buildingMapper.updateById(building);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 楼栋删除保护：已生成房源的楼栋禁止删除 */
    @Transactional
    public void deleteBuilding(Long projectId, Long buildingId) {
        requireBuilding(projectId, buildingId);
        Long roomCnt = roomMapper.selectCount(new LambdaQueryWrapper<MdmRoom>().eq(MdmRoom::getBuildingId, buildingId));
        if (roomCnt > 0) throw new BizException("该楼栋已生成房源，请先删除房间");
        buildingMapper.deleteById(buildingId);
    }

    private MdmBuilding requireBuilding(Long projectId, Long buildingId) {
        MdmBuilding b = buildingMapper.selectById(buildingId);
        if (b == null || !b.getProjectId().equals(projectId)) throw new BizException("楼栋不存在");
        return b;
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) throw new BizException("项目不存在");
    }
}
