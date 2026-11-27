package com.reip.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.mdm.entity.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MDM 模块 Mapper 集合（小 Mapper 统一收纳，风格同 platform 模块）
 */
public interface MdmMappers {

    @Mapper interface OrgMapper extends BaseMapper<MdmOrg> {}

    @Mapper interface ProjectMapper extends BaseMapper<MdmProject> {}

    @Mapper interface PhaseMapper extends BaseMapper<MdmPhase> {}

    @Mapper interface ProductTypeMapper extends BaseMapper<MdmProductType> {}

    @Mapper interface BuildingMapper extends BaseMapper<MdmBuilding> {}

    @Mapper interface CostAccountMapper extends BaseMapper<MdmCostAccount> {}

    @Mapper interface ExpenseAccountMapper extends BaseMapper<MdmExpenseAccount> {}

    @Mapper interface CategoryMapper extends BaseMapper<MdmCategory> {}

    @Mapper interface DictMapper extends BaseMapper<MdmDict> {}

    @Mapper interface CustomerMapper extends BaseMapper<MdmCustomer> {}

    @Mapper interface CustomerRelativeMapper extends BaseMapper<MdmCustomerRelative> {}

    @Mapper interface DistributeLogMapper extends BaseMapper<MdmDistributeLog> {}

    @Mapper
    interface EmployeeMapper extends BaseMapper<MdmEmployee> {
        /** 员工分页：关联登录账号姓名与所属组织名称 */
        @Select("""
            <script>
            SELECT e.*, u.real_name AS user_name, o.name AS org_name
            FROM mdm_employee e
            LEFT JOIN sys_user u ON u.id = e.user_id AND u.deleted = 0
            LEFT JOIN mdm_org o ON o.id = e.org_id AND o.deleted = 0
            WHERE e.deleted = 0
            <if test="kw != null and kw != ''">
              AND (e.real_name LIKE CONCAT('%', #{kw}, '%')
                OR e.emp_no LIKE CONCAT('%', #{kw}, '%')
                OR e.mobile LIKE CONCAT('%', #{kw}, '%'))
            </if>
            <if test="status != null and status != ''">AND e.status = #{status}</if>
            <if test="orgId != null">AND e.org_id = #{orgId}</if>
            ORDER BY e.id DESC
            </script>
            """)
        IPage<MdmEmployee> selectEmployeePage(Page<MdmEmployee> page, @Param("kw") String kw,
                                              @Param("status") String status, @Param("orgId") Long orgId);
    }

    @Mapper
    interface RoomMapper extends BaseMapper<MdmRoom> {
        /** 房间分页：带出楼栋/项目展示信息 */
        @Select("""
            <script>
            SELECT r.*, b.name AS building_name, b.building_code AS building_code,
                   p.name AS project_name, p.project_code AS project_code
            FROM mdm_room r
            LEFT JOIN mdm_building b ON b.id = r.building_id AND b.deleted = 0
            LEFT JOIN mdm_project p ON p.id = r.project_id AND p.deleted = 0
            WHERE r.deleted = 0
            <if test="projectId != null">AND r.project_id = #{projectId}</if>
            <if test="buildingId != null">AND r.building_id = #{buildingId}</if>
            <if test="saleStatus != null and saleStatus != ''">AND r.sale_status = #{saleStatus}</if>
            <if test="kw != null and kw != ''">AND r.room_code LIKE CONCAT('%', #{kw}, '%')</if>
            ORDER BY r.building_id, r.floor_no, r.unit_no, r.room_code
            </script>
            """)
        IPage<MdmRoom> selectRoomPage(Page<MdmRoom> page, @Param("projectId") Long projectId,
                                      @Param("buildingId") Long buildingId, @Param("saleStatus") String saleStatus,
                                      @Param("kw") String kw);

        /** 楼栋看板：该楼栋全部房间 + 一房一档（表价/底价/销控状态），按楼层/单元/房号排序 */
        @Select("""
            SELECT r.id AS roomId, r.room_code AS roomCode, r.floor_no AS floorNo, r.unit_no AS unitNo,
                   r.room_no AS roomNo, r.layout AS layout, r.orientation AS orientation,
                   r.area AS area, r.inner_area AS innerArea, r.saleable AS saleable, r.sale_status AS roomStatus,
                   rs.table_price AS tablePrice, rs.floor_price AS floorPrice, rs.status AS saleCtrlStatus
            FROM mdm_room r
            LEFT JOIN sale_room_status rs ON rs.room_id = r.id AND rs.deleted = 0 AND rs.status <> 'REFUNDED'
            WHERE r.deleted = 0 AND r.building_id = #{buildingId}
            ORDER BY r.floor_no, r.unit_no, r.room_no, r.id
            """)
        java.util.List<java.util.Map<String, Object>> selectBuildingRooms(@Param("buildingId") Long buildingId);

        /** 楼栋汇总：按楼栋统计房间数/面积/可售/已售/表价货值 */
        @Select("""
            SELECT b.id AS buildingId, b.building_code AS buildingCode, b.name AS buildingName,
                   b.floors AS floors, b.underground_floors AS undergroundFloors, b.units AS units,
                   b.total_area AS totalArea, b.status AS status,
                   COUNT(r.id) AS roomCount,
                   COALESCE(SUM(r.area), 0) AS roomArea,
                   COALESCE(SUM(CASE WHEN rs.status = 'SELLABLE' THEN 1 ELSE 0 END), 0) AS sellableCount,
                   COALESCE(SUM(CASE WHEN rs.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN 1 ELSE 0 END), 0) AS soldCount,
                   COALESCE(SUM(rs.table_price), 0) AS tableValue
            FROM mdm_building b
            LEFT JOIN mdm_room r ON r.building_id = b.id AND r.deleted = 0
            LEFT JOIN sale_room_status rs ON rs.room_id = r.id AND rs.deleted = 0 AND rs.status <> 'REFUNDED'
            WHERE b.deleted = 0 AND b.project_id = #{projectId}
            GROUP BY b.id, b.building_code, b.name, b.floors, b.underground_floors, b.units, b.total_area, b.status
            ORDER BY b.id
            """)
        java.util.List<java.util.Map<String, Object>> selectBuildingSummary(@Param("projectId") Long projectId);
    }

    @Mapper
    interface SupplierMapper extends BaseMapper<MdmSupplier> {
        /** 供应商被合同引用数（删除保护用） */
        @Select("SELECT COUNT(*) FROM cost_contract WHERE supplier_id = #{supplierId} AND deleted = 0")
        Long countContractRef(@Param("supplierId") Long supplierId);
    }

    @Mapper
    interface SupplierQualificationMapper extends BaseMapper<MdmSupplierQualification> {
        /** 查询 days 天内到期的资质证照（带供应商信息） */
        @Select("""
            SELECT q.*, s.name AS supplier_name, s.supplier_code AS supplier_code
            FROM mdm_supplier_qualification q
            JOIN mdm_supplier s ON s.id = q.supplier_id AND s.deleted = 0
            WHERE q.deleted = 0 AND q.expire_date IS NOT NULL
              AND q.expire_date >= CURRENT_DATE
              AND q.expire_date <= DATE_ADD(CURRENT_DATE, INTERVAL #{days} DAY)
            ORDER BY q.expire_date
            """)
        List<MdmSupplierQualification> selectExpiring(@Param("days") int days);
    }

    @Mapper interface QualityIssueMapper extends BaseMapper<MdmQualityIssue> {}

    @Mapper interface CodeRuleMapper extends BaseMapper<MdmCodeRule> {
        /** 读取计数器当前值（预览用，不加锁、不消耗计数） */
        @Select("SELECT current_no FROM mdm_code_counter WHERE rule_code = #{ruleCode} AND dim_key = #{dimKey}")
        Long findCurrentNo(@Param("ruleCode") String ruleCode, @Param("dimKey") String dimKey);
    }
}
