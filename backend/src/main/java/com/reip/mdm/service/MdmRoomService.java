package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmBuilding;
import com.reip.mdm.entity.MdmProductType;
import com.reip.mdm.entity.MdmProject;
import com.reip.mdm.entity.MdmRoom;
import com.reip.mdm.mapper.MdmMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房间服务：房间生成器、分页查询、销控保护
 */
@Service
@RequiredArgsConstructor
public class MdmRoomService {

    private final MdmMappers.RoomMapper roomMapper;
    private final MdmMappers.BuildingMapper buildingMapper;
    private final MdmMappers.ProjectMapper projectMapper;
    private final MdmMappers.ProductTypeMapper productTypeMapper;

    /** 房间分页（可按项目/楼栋/销控状态过滤，带楼栋与项目展示信息） */
    public PageResult<MdmRoom> page(long current, long size, Long projectId, Long buildingId, String saleStatus, String kw) {
        IPage<MdmRoom> p = roomMapper.selectRoomPage(new Page<>(current, size), projectId, buildingId, saleStatus, kw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 房间批量生成：按 楼层×单元×户室
     * room_code 规则：{项目code}-{楼栋code}-{楼层}{单元}{两位序号}
     * 业态默认取项目第一个业态；已存在的编号自动跳过
     */
    @Transactional
    public Map<String, Integer> generate(GenerateForm form) {
        if (form == null || form.getBuildingId() == null) throw new BizException("缺少楼栋ID");
        MdmBuilding building = buildingMapper.selectById(form.getBuildingId());
        if (building == null) throw new BizException("楼栋不存在");
        MdmProject project = projectMapper.selectById(building.getProjectId());
        if (project == null) throw new BizException("项目不存在");

        if (form.getStartFloor() == null || form.getEndFloor() == null) throw new BizException("请填写楼层范围");
        if (form.getStartFloor() > form.getEndFloor()) throw new BizException("起始楼层不能大于结束楼层");
        int units = form.getUnits() != null ? form.getUnits()
                : (building.getUnits() != null ? building.getUnits() : 1);
        if (units < 1) throw new BizException("单元数至少为1");
        if (form.getRoomsPerFloor() == null || form.getRoomsPerFloor() < 1) throw new BizException("每层户室数至少为1");

        // 业态默认取项目第一个业态
        List<MdmProductType> pts = productTypeMapper.selectList(new LambdaQueryWrapper<MdmProductType>()
                .eq(MdmProductType::getProjectId, project.getId())
                .orderByAsc(MdmProductType::getSort).orderByAsc(MdmProductType::getId)
                .last("LIMIT 1"));
        Long productTypeId = pts.isEmpty() ? null : pts.get(0).getId();

        // 楼栋下已有编号：重复生成时跳过，保证幂等
        Set<String> existing = roomMapper.selectList(new LambdaQueryWrapper<MdmRoom>()
                        .select(MdmRoom::getRoomCode).eq(MdmRoom::getBuildingId, building.getId()))
                .stream().map(MdmRoom::getRoomCode).collect(Collectors.toSet());

        int generated = 0;
        int skipped = 0;
        for (int floor = form.getStartFloor(); floor <= form.getEndFloor(); floor++) {
            String floorLabel = floor >= 0 ? String.valueOf(floor) : "B" + Math.abs(floor);
            for (int unit = 1; unit <= units; unit++) {
                for (int seq = 1; seq <= form.getRoomsPerFloor(); seq++) {
                    String roomNo = String.format("%02d", seq);
                    String code = project.getProjectCode() + "-" + building.getBuildingCode()
                            + "-" + floorLabel + unit + roomNo;
                    if (existing.contains(code)) {
                        skipped++;
                        continue;
                    }
                    MdmRoom room = new MdmRoom();
                    room.setProjectId(project.getId());
                    room.setBuildingId(building.getId());
                    room.setRoomCode(code);
                    room.setFloorNo(floor);
                    room.setUnitNo(unit);
                    room.setRoomNo(roomNo);
                    room.setProductTypeId(productTypeId);
                    room.setSaleable(1);
                    room.setSaleStatus("INIT");
                    roomMapper.insert(room);
                    existing.add(code);
                    generated++;
                }
            }
        }
        return Map.of("generated", generated, "skipped", skipped);
    }

    /** 房间修改：销控状态非 INIT 时禁止修改户型/面积等属性 */
    @Transactional
    public void update(MdmRoom room) {
        if (room.getId() == null) throw new BizException("缺少房间ID");
        MdmRoom old = roomMapper.selectById(room.getId());
        if (old == null) throw new BizException("房间不存在");
        if (!"INIT".equals(old.getSaleStatus())) {
            boolean changed = !eq(room.getLayout(), old.getLayout())
                    || !eq(room.getOrientation(), old.getOrientation())
                    || !eq(room.getArea(), old.getArea())
                    || !eq(room.getInnerArea(), old.getInnerArea())
                    || !eq(room.getProductTypeId(), old.getProductTypeId())
                    || !eq(room.getSaleable(), old.getSaleable())
                    || !eq(room.getRoomNo(), old.getRoomNo());
            if (changed) throw new BizException("房源已被销控锁定，属性不可修改");
            return; // 无属性变化，直接返回
        }
        // 编号/归属/销控状态不可经此接口修改（销控由销售域回写）
        room.setRoomCode(null);
        room.setProjectId(null);
        room.setBuildingId(null);
        room.setSaleStatus(null);
        int updated = roomMapper.updateById(room);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 入参为空或与旧值一致视为未变更 */
    private boolean eq(Object incoming, Object old) {
        return incoming == null || incoming.equals(old);
    }

    @Transactional
    public void delete(Long id) {
        roomMapper.deleteById(id);
    }

    /** 房间生成器参数 */
    @Data
    public static class GenerateForm {
        /** 楼栋ID */
        private Long buildingId;
        /** 起始楼层（负数为地下层） */
        private Integer startFloor;
        /** 结束楼层 */
        private Integer endFloor;
        /** 单元数（缺省取楼栋单元数） */
        private Integer units;
        /** 每层户室数 */
        private Integer roomsPerFloor;
    }
}
