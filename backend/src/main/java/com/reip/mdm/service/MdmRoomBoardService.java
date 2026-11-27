package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.mdm.entity.MdmRoom;
import com.reip.mdm.mapper.MdmMappers;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 楼栋房源看板：楼栋汇总 / 楼栋房间明细(含表价底价销控) / 合并房间
 */
@Service
@RequiredArgsConstructor
public class MdmRoomBoardService {

    private final MdmMappers.RoomMapper roomMapper;
    private final SaleMappers.RoomStatusMapper statusMapper;

    /** 楼栋汇总（按楼栋统计房间/面积/可售/已售/货值） */
    public List<Map<String, Object>> buildingSummary(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        return roomMapper.selectBuildingSummary(projectId);
    }

    /** 楼栋房间明细（可视化看板数据源） */
    public List<Map<String, Object>> buildingRooms(Long buildingId) {
        if (buildingId == null) throw new BizException("缺少楼栋ID");
        return roomMapper.selectBuildingRooms(buildingId);
    }

    /**
     * 合并房间：所选房间须同楼栋/同楼层/同单元且均可售（无认购签约锁定），
     * 面积、表价、底价汇总到目标房间（ID 最小者），其余房间及其销控记录删除。
     */
    @Transactional
    public Map<String, Object> merge(List<Long> roomIds) {
        if (roomIds == null || roomIds.size() < 2) throw new BizException("合并需至少选择 2 个房间");
        List<MdmRoom> rooms = roomIds.stream().map(roomMapper::selectById)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (rooms.size() < 2) throw new BizException("所选房间数量不足或不存在");

        MdmRoom first = rooms.get(0);
        for (MdmRoom r : rooms) {
            if (!Objects.equals(r.getBuildingId(), first.getBuildingId())) throw new BizException("仅可合并同一楼栋的房间");
            if (!Objects.equals(r.getFloorNo(), first.getFloorNo())) throw new BizException("仅可合并同一楼层的房间");
            if (!Objects.equals(r.getUnitNo(), first.getUnitNo())) throw new BizException("仅可合并同一单元的房间");
        }
        for (MdmRoom r : rooms) {
            if (!"INIT".equals(r.getSaleStatus())) throw new BizException("房间 " + r.getRoomCode() + " 已被占用，不可合并");
            SaleRoomStatus rs = statusOf(r.getId());
            if (rs != null && !"SELLABLE".equals(rs.getStatus()))
                throw new BizException("房间 " + r.getRoomCode() + " 非可售状态，不可合并");
        }

        rooms.sort(Comparator.comparing(MdmRoom::getId));
        MdmRoom target = rooms.get(0);
        List<MdmRoom> others = rooms.subList(1, rooms.size());

        BigDecimal area = BigDecimal.ZERO, inner = BigDecimal.ZERO;
        for (MdmRoom r : rooms) {
            area = area.add(nvl(r.getArea()));
            inner = inner.add(nvl(r.getInnerArea()));
        }
        target.setArea(area);
        target.setInnerArea(inner);
        roomMapper.updateById(target);

        SaleRoomStatus targetRs = statusOf(target.getId());
        BigDecimal table = BigDecimal.ZERO, floor = BigDecimal.ZERO;
        boolean anyPrice = false;
        for (MdmRoom r : rooms) {
            SaleRoomStatus rs = statusOf(r.getId());
            if (rs == null) continue;
            if (rs.getTablePrice() != null) { table = table.add(rs.getTablePrice()); anyPrice = true; }
            if (rs.getFloorPrice() != null) floor = floor.add(rs.getFloorPrice());
        }
        if (anyPrice) {
            if (targetRs == null) {
                targetRs = new SaleRoomStatus();
                targetRs.setProjectId(target.getProjectId());
                targetRs.setRoomId(target.getId());
                targetRs.setRoomCode(target.getRoomCode());
                targetRs.setBuildingId(target.getBuildingId());
                targetRs.setFloorNo(target.getFloorNo());
                targetRs.setUnitNo(target.getUnitNo());
                targetRs.setStatus("SELLABLE");
            }
            targetRs.setArea(area);
            targetRs.setTablePrice(table);
            targetRs.setFloorPrice(floor.signum() == 0 ? null : floor);
            if (targetRs.getId() == null) statusMapper.insert(targetRs);
            else if (statusMapper.updateById(targetRs) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }

        for (MdmRoom r : others) {
            roomMapper.deleteById(r.getId());
            SaleRoomStatus rs = statusOf(r.getId());
            if (rs != null) statusMapper.deleteById(rs.getId());
        }

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("mergedInto", target.getRoomCode());
        ret.put("area", area);
        ret.put("tablePrice", table);
        ret.put("floorPrice", floor);
        ret.put("removed", others.size());
        return ret;
    }

    private SaleRoomStatus statusOf(Long roomId) {
        return statusMapper.selectOne(new LambdaQueryWrapper<SaleRoomStatus>()
                .eq(SaleRoomStatus::getRoomId, roomId)
                .ne(SaleRoomStatus::getStatus, "REFUNDED")
                .last("LIMIT 1"));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
