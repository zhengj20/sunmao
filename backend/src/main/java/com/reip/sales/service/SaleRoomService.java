package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.sales.entity.SaleRoomStatus;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房源销控（一房一档）：初始化 / 锁定放开 / 状态推进 / 筛选查询
 * 状态链 R1：SELLABLE→LOCKED→SMALL_DEPOSIT→BIG_DEPOSIT→SUBSCRIBED→CONTRACTED→FILED；退房释放回 SELLABLE
 */
@Service
@RequiredArgsConstructor
public class SaleRoomService {

    /** 表价/底价默认单价（元/㎡），与 V19 种子口径一致 */
    private static final BigDecimal TABLE_UNIT = new BigDecimal("29800");
    private static final BigDecimal FLOOR_UNIT = new BigDecimal("26800");

    private final SaleMappers.RoomStatusMapper roomMapper;
    private final SaleMappers.MdmLookupMapper mdm;
    private final SaleMappers.ReportMapper reportMapper;

    // ==================== 查询 ====================

    public PageResult<SaleRoomStatus> page(long current, long size, Long projectId, Long buildingId,
                                           String status, String kw, BigDecimal minArea, BigDecimal maxArea) {
        LambdaQueryWrapper<SaleRoomStatus> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleRoomStatus::getProjectId, projectId);
        if (buildingId != null) qw.eq(SaleRoomStatus::getBuildingId, buildingId);
        if (status != null && !status.isBlank()) qw.eq(SaleRoomStatus::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(SaleRoomStatus::getRoomCode, kw);
        if (minArea != null) qw.ge(SaleRoomStatus::getArea, minArea);
        if (maxArea != null) qw.le(SaleRoomStatus::getArea, maxArea);
        qw.orderByAsc(SaleRoomStatus::getBuildingId).orderByAsc(SaleRoomStatus::getFloorNo).orderByAsc(SaleRoomStatus::getRoomCode);
        Page<SaleRoomStatus> p = roomMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(r -> r.setLayout(roomLayout(r.getRoomId())));
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SaleRoomStatus detail(Long roomId) {
        return require(roomId);
    }

    /** 有效房源状态（非退房）：一房一档，同 room 仅一条非退房记录 */
    public SaleRoomStatus require(Long roomId) {
        if (roomId == null) throw new BizException("缺少房源ID");
        SaleRoomStatus r = roomMapper.selectOne(new LambdaQueryWrapper<SaleRoomStatus>()
                .eq(SaleRoomStatus::getRoomId, roomId)
                .ne(SaleRoomStatus::getStatus, "REFUNDED")
                .last("LIMIT 1"));
        if (r == null) throw new BizException("该房源尚未纳入销控：" + roomId);
        return r;
    }

    public BigDecimal tablePriceOf(Long roomId) {
        return SaleSupport.nvl(require(roomId).getTablePrice());
    }

    // ==================== 初始化（从 MDM 房间生成） ====================

    /** 从 mdm_room 生成销控记录（幂等：已纳入的房间跳过） */
    @Transactional
    public int initFromMdm(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        List<java.util.Map<String, Object>> rooms = mdm.selectMdmRooms(projectId);
        int created = 0;
        for (java.util.Map<String, Object> m : rooms) {
            Long roomId = toLong(m.get("id"));
            if (roomId == null) continue;
            Long exists = roomMapper.selectCount(new LambdaQueryWrapper<SaleRoomStatus>()
                    .eq(SaleRoomStatus::getRoomId, roomId));
            if (exists != null && exists > 0) continue;
            BigDecimal area = toDecimal(m.get("area"));
            SaleRoomStatus r = new SaleRoomStatus();
            r.setProjectId(projectId);
            r.setRoomId(roomId);
            r.setRoomCode(str(m.get("room_code")));
            r.setBuildingId(toLong(m.get("building_id")));
            r.setFloorNo(toInt(m.get("floor_no")));
            r.setUnitNo(toInt(m.get("unit_no")));
            r.setArea(area);
            r.setTablePrice(area.multiply(TABLE_UNIT).setScale(2, RoundingMode.HALF_UP));
            r.setFloorPrice(area.multiply(FLOOR_UNIT).setScale(2, RoundingMode.HALF_UP));
            r.setStatus("SELLABLE");
            roomMapper.insert(r);
            created++;
        }
        return created;
    }

    // ==================== 锁定 / 放开 ====================

    /** 锁定：仅 SELLABLE 可锁定（R1 前向） */
    @Transactional
    public void lock(Long roomId, Long lockBy, LocalDateTime lockExpire) {
        SaleRoomStatus r = require(roomId);
        if (!"SELLABLE".equals(r.getStatus())) throw new BizException("仅可售房源可锁定，当前：" + r.getStatus());
        r.setStatus("LOCKED");
        r.setLockBy(lockBy);
        r.setLockExpire(lockExpire);
        save(r);
    }

    /** 放开：仅 LOCKED 可放开，回 SELLABLE */
    @Transactional
    public void unlock(Long roomId) {
        SaleRoomStatus r = require(roomId);
        if (!"LOCKED".equals(r.getStatus())) throw new BizException("仅已锁定房源可放开，当前：" + r.getStatus());
        r.setStatus("SELLABLE");
        r.setLockBy(null);
        r.setLockExpire(null);
        save(r);
    }

    /** 预占（认购登记）：SELLABLE→LOCKED 并记录关联单据，防止并发重复认购 */
    @Transactional
    public void hold(Long roomId, String bizNo) {
        SaleRoomStatus r = require(roomId);
        if (!"SELLABLE".equals(r.getStatus()))
            throw new BizException("该房源当前不可认购（状态：" + r.getStatus() + "）");
        r.setStatus("LOCKED");
        r.setBizNo(bizNo);
        save(r);
    }

    // ==================== 状态推进（供认购/签约/退房回写调用） ====================

    /** 认购生效 → SUBSCRIBED */
    @Transactional
    public void setSubscribed(Long roomId, String bizNo) {
        SaleRoomStatus r = require(roomId);
        if (!"SELLABLE".equals(r.getStatus()) && !"LOCKED".equals(r.getStatus()))
            throw new BizException("该房源非可售/锁定态，无法认购：" + r.getStatus());
        r.setStatus("SUBSCRIBED");
        r.setBizNo(bizNo);
        save(r);
    }

    /** 签约生效 → CONTRACTED */
    @Transactional
    public void setContracted(Long roomId, String bizNo) {
        SaleRoomStatus r = require(roomId);
        if (!"SUBSCRIBED".equals(r.getStatus()) && !"SELLABLE".equals(r.getStatus()) && !"LOCKED".equals(r.getStatus()))
            throw new BizException("该房源非认购/可售态，无法签约：" + r.getStatus());
        r.setStatus("CONTRACTED");
        r.setBizNo(bizNo);
        save(r);
    }

    /** 退房/退定释放 → SELLABLE（清空关联单据；显式 set null 以覆盖旧值） */
    @Transactional
    public void release(Long roomId) {
        require(roomId);
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SaleRoomStatus> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SaleRoomStatus>()
                        .eq(SaleRoomStatus::getRoomId, roomId)
                        .ne(SaleRoomStatus::getStatus, "REFUNDED")
                        .set(SaleRoomStatus::getStatus, "SELLABLE")
                        .set(SaleRoomStatus::getBizNo, null)
                        .set(SaleRoomStatus::getLockBy, null)
                        .set(SaleRoomStatus::getLockExpire, null);
        roomMapper.update(null, uw);
    }

    /** 可认购校验：房源须为 SELLABLE（未锁定、未认购/签约） */
    public void assertSellable(Long roomId) {
        SaleRoomStatus r = require(roomId);
        if (!"SELLABLE".equals(r.getStatus()))
            throw new BizException("该房源当前不可认购（状态：" + r.getStatus() + "，" + lockHint(r) + "）");
    }

    // ==================== 私有 ====================

    private String lockHint(SaleRoomStatus r) {
        if (r.getLockBy() == null) return "";
        String name = r.getLockBy() == null ? "" : safeName(r.getLockBy());
        return name.isEmpty() ? "" : "，被 " + name + " 锁定";
    }

    private String safeName(Long uid) {
        String n = mdm.selectUserName(uid);
        return n == null ? "" : n;
    }

    private String roomLayout(Long roomId) {
        return mdm.selectRoomLayout(roomId);
    }

    private void save(SaleRoomStatus r) {
        if (roomMapper.updateById(r) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    private static Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}
