package com.reip.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.sales.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 销售模块 Mapper 集合（模式同 CostMappers / ProcMappers）
 * 含跨模块只读查询：mdm_project / mdm_room / mdm_customer / sys_user / wfl_instance
 */
public interface SaleMappers {

    @Mapper
    interface RoomStatusMapper extends BaseMapper<SaleRoomStatus> {}

    @Mapper
    interface SubscriptionMapper extends BaseMapper<SaleSubscription> {}

    @Mapper
    interface ContractMapper extends BaseMapper<SaleContract> {}

    @Mapper
    interface PaymentPlanMapper extends BaseMapper<SalePaymentPlan> {}

    @Mapper
    interface ReceiptMapper extends BaseMapper<SaleReceipt> {}

    @Mapper
    interface MortgageMapper extends BaseMapper<SaleMortgage> {}

    @Mapper
    interface RefundExchangeMapper extends BaseMapper<SaleRefundExchange> {}

    @Mapper
    interface DiscountMapper extends BaseMapper<SaleDiscount> {}

    @Mapper
    interface VisitMapper extends BaseMapper<SaleVisit> {}

    @Mapper
    interface FollowMapper extends BaseMapper<SaleFollow> {}

    @Mapper
    interface TargetMapper extends BaseMapper<SaleTarget> {}

    @Mapper
    interface ChannelMapper extends BaseMapper<SaleChannel> {}

    @Mapper
    interface AreaDiffMapper extends BaseMapper<SaleAreaDiff> {}

    @Mapper
    interface IncomingMapper extends BaseMapper<SaleIncoming> {}

    /** MDM/平台主数据只读查询（编码规则上下文 / 展示名称带出） */
    @Mapper
    interface MdmLookupMapper {

        @Select("SELECT project_code FROM mdm_project WHERE id = #{id} AND deleted = 0")
        String selectProjectCode(Long id);

        @Select("SELECT room_code FROM mdm_room WHERE id = #{id} AND deleted = 0")
        String selectRoomCode(Long id);

        @Select("SELECT id, room_code, building_id, floor_no, unit_no, area FROM mdm_room "
                + "WHERE room_code = #{code} AND deleted = 0 ORDER BY id DESC LIMIT 1")
        java.util.Map<String, Object> selectRoomByCode(@Param("code") String code);

        @Select("SELECT name FROM mdm_customer WHERE id = #{id} AND deleted = 0")
        String selectCustomerName(Long id);

        @Select("SELECT real_name FROM sys_user WHERE id = #{id} AND deleted = 0")
        String selectUserName(Long id);

        @Select("SELECT layout FROM mdm_room WHERE id = #{id} AND deleted = 0")
        String selectRoomLayout(Long id);

        @Select("SELECT id, room_code, building_id, floor_no, unit_no, area FROM mdm_room "
                + "WHERE project_id = #{projectId} AND deleted = 0 AND saleable = 1 ORDER BY id")
        java.util.List<java.util.Map<String, Object>> selectMdmRooms(@Param("projectId") Long projectId);
    }

    /** 折扣刚性 R3：查是否存在已批准的特价房审批单（wfl_instance 只读） */
    @Mapper
    interface ApprovalLookupMapper {

        @Select("SELECT COUNT(*) FROM wfl_instance WHERE biz_type = 'SALE_SPECIAL_PRICE' "
                + "AND biz_id = #{contractId} AND status = 'APPROVED' AND deleted = 0")
        long countApprovedSpecialPrice(@Param("contractId") Long contractId);

        /** AGENT 级有效折扣方案的最大可下探折扣率（取最宽松一条） */
        @Select("SELECT MIN(max_rate) FROM sale_discount WHERE level = 'AGENT' AND status = 'ACTIVE' "
                + "AND deleted = 0 AND effective_from <= CURDATE() AND effective_to >= CURDATE()")
        java.math.BigDecimal selectAgentMaxRate();
    }

    /** 来账自动匹配 / 面积补差导入 候选查询 */
    @Mapper
    interface MatchMapper {

        /** 项目下生效合同 + 客户名 + 房号（来账自动匹配候选） */
        @Select("SELECT c.id AS contractId, c.contract_no AS contractNo, c.room_id AS roomId, "
                + "r.room_code AS roomCode, cu.name AS customerName "
                + "FROM sale_contract c LEFT JOIN mdm_room r ON r.id = c.room_id AND r.deleted = 0 "
                + "LEFT JOIN mdm_customer cu ON cu.id = c.customer_id AND cu.deleted = 0 "
                + "WHERE c.project_id = #{projectId} AND c.status IN ('ACTIVE','FILED') AND c.deleted = 0 ORDER BY c.id")
        java.util.List<java.util.Map<String, Object>> selectMatchCandidates(@Param("projectId") Long projectId);

        /** 面积补差导入：按房间取最近一份生效合同(成交价/客户) */
        @Select("SELECT c.id AS contractId, c.contract_no AS contractNo, c.contract_price AS contractPrice, "
                + "c.customer_id AS customerId, cu.name AS customerName "
                + "FROM sale_contract c LEFT JOIN mdm_customer cu ON cu.id = c.customer_id AND cu.deleted = 0 "
                + "WHERE c.room_id = #{roomId} AND c.status IN ('ACTIVE','FILED') AND c.deleted = 0 ORDER BY c.id DESC LIMIT 1")
        java.util.Map<String, Object> selectContractByRoom(@Param("roomId") Long roomId);
    }

    /** 销售报表聚合查询 */
    @Mapper
    interface ReportMapper {

        /** 认购口径（有效认购：ACTIVE/CONVERTED/APPROVING） */
        @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(deposit), 0) AS amt FROM sale_subscription "
                + "WHERE deleted = 0 AND project_id = #{projectId} AND status IN ('ACTIVE','CONVERTED','APPROVING')")
        Map<String, Object> selectSubscriptionStats(@Param("projectId") Long projectId);

        /** 签约口径（生效/网签合同） */
        @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(contract_price), 0) AS amt FROM sale_contract "
                + "WHERE deleted = 0 AND project_id = #{projectId} AND status IN ('ACTIVE','FILED')")
        Map<String, Object> selectContractStats(@Param("projectId") Long projectId);

        /** 回款口径（实收） */
        @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(r.received_amount), 0) AS amt "
                + "FROM sale_receipt r JOIN sale_contract c ON c.id = r.contract_id AND c.deleted = 0 "
                + "WHERE r.deleted = 0 AND c.project_id = #{projectId}")
        Map<String, Object> selectReceiptStats(@Param("projectId") Long projectId);

        /** 货值/去化（一房一档口径） */
        @Select("SELECT COUNT(*) AS totalRooms, COALESCE(SUM(table_price), 0) AS totalValue, "
                + "COALESCE(SUM(CASE WHEN status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN 1 ELSE 0 END), 0) AS soldRooms, "
                + "COALESCE(SUM(CASE WHEN status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN table_price ELSE 0 END), 0) AS soldValue "
                + "FROM sale_room_status WHERE deleted = 0 AND project_id = #{projectId}")
        Map<String, Object> selectRoomStats(@Param("projectId") Long projectId);

        /** 应收/实收（回款率） */
        @Select("SELECT COALESCE(SUM(p.plan_amount), 0) AS total, COALESCE(SUM(p.received_amount), 0) AS received "
                + "FROM sale_payment_plan p JOIN sale_contract c ON c.id = p.contract_id AND c.deleted = 0 "
                + "WHERE p.deleted = 0 AND c.project_id = #{projectId}")
        Map<String, Object> selectPlanStats(@Param("projectId") Long projectId);

        /** 成交均价（价格实现率分子） */
        @Select("SELECT COALESCE(AVG(contract_price), 0) FROM sale_contract "
                + "WHERE deleted = 0 AND project_id = #{projectId} AND status IN ('ACTIVE','FILED')")
        java.math.BigDecimal selectAvgContractPrice(@Param("projectId") Long projectId);
    }

    // =====================================================
    // 驾驶舱 / 总览 / 顾问 / 漏斗 聚合（只读，基于 sale_* + mdm 主数据）
    // =====================================================
    @Mapper
    interface DashboardMapper {

        /** 月度签约趋势：[{period, cnt, amt}] */
        @Select("SELECT DATE_FORMAT(signed_at, '%Y-%m') AS period, COUNT(*) AS cnt, COALESCE(SUM(contract_price),0) AS amt "
                + "FROM sale_contract WHERE deleted = 0 AND project_id = #{projectId} AND status IN ('ACTIVE','FILED') AND signed_at IS NOT NULL "
                + "GROUP BY period ORDER BY period")
        java.util.List<java.util.Map<String, Object>> selectMonthlyContract(@Param("projectId") Long projectId);

        /** 月度回款趋势：[{period, cnt, amt}] */
        @Select("SELECT DATE_FORMAT(r.received_at, '%Y-%m') AS period, COUNT(*) AS cnt, COALESCE(SUM(r.received_amount),0) AS amt "
                + "FROM sale_receipt r JOIN sale_contract c ON c.id = r.contract_id AND c.deleted = 0 "
                + "WHERE r.deleted = 0 AND c.project_id = #{projectId} AND r.received_at IS NOT NULL "
                + "GROUP BY period ORDER BY period")
        java.util.List<java.util.Map<String, Object>> selectMonthlyReceipt(@Param("projectId") Long projectId);

        /** 月度成交均价(元/㎡)：[{period, avgPrice}] */
        @Select("SELECT DATE_FORMAT(c.signed_at, '%Y-%m') AS period, "
                + "COALESCE(SUM(c.contract_price),0) / NULLIF(COALESCE(SUM(r.area),0),0) AS avgPrice "
                + "FROM sale_contract c LEFT JOIN mdm_room r ON r.id = c.room_id AND r.deleted = 0 "
                + "WHERE c.deleted = 0 AND c.project_id = #{projectId} AND c.status IN ('ACTIVE','FILED') AND c.signed_at IS NOT NULL "
                + "GROUP BY period ORDER BY period")
        java.util.List<java.util.Map<String, Object>> selectMonthlyAvgPrice(@Param("projectId") Long projectId);

        /** 蓄客漏斗：来访量 */
        @Select("SELECT COUNT(*) FROM sale_visit WHERE deleted = 0 AND project_id = #{projectId}")
        long selectVisitCount(@Param("projectId") Long projectId);

        /** 蓄客漏斗：已回款合同数（去重） */
        @Select("SELECT COUNT(DISTINCT c.id) FROM sale_receipt r JOIN sale_contract c ON c.id = r.contract_id AND c.deleted = 0 "
                + "WHERE r.deleted = 0 AND c.project_id = #{projectId}")
        long selectPaidContractCount(@Param("projectId") Long projectId);

        /** 渠道来访结构：[{channel, cnt}] */
        @Select("SELECT COALESCE(channel, '其他') AS channel, COUNT(*) AS cnt "
                + "FROM sale_visit WHERE deleted = 0 AND project_id = #{projectId} GROUP BY channel ORDER BY cnt DESC")
        java.util.List<java.util.Map<String, Object>> selectVisitByChannel(@Param("projectId") Long projectId);

        /** 渠道成交结构（合同中介）：[{channel, cnt, amt}] */
        @Select("SELECT supplier_name AS channel, COUNT(*) AS cnt, COALESCE(SUM(contract_price),0) AS amt "
                + "FROM sale_contract WHERE deleted = 0 AND project_id = #{projectId} AND status IN ('ACTIVE','FILED') AND supplier_name IS NOT NULL "
                + "GROUP BY supplier_name ORDER BY amt DESC")
        java.util.List<java.util.Map<String, Object>> selectContractBySupplier(@Param("projectId") Long projectId);

        /** 楼栋销控看板：[{buildingCode, buildingName, total, sold, totalValue, soldValue, totalArea}] */
        @Select("SELECT b.building_code AS buildingCode, b.name AS buildingName, "
                + "COUNT(rs.id) AS total, "
                + "SUM(CASE WHEN rs.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN 1 ELSE 0 END) AS sold, "
                + "COALESCE(SUM(rs.table_price),0) AS totalValue, "
                + "COALESCE(SUM(CASE WHEN rs.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN rs.table_price ELSE 0 END),0) AS soldValue, "
                + "COALESCE(SUM(rs.area),0) AS totalArea "
                + "FROM sale_room_status rs LEFT JOIN mdm_building b ON b.id = rs.building_id AND b.deleted = 0 "
                + "WHERE rs.deleted = 0 AND rs.project_id = #{projectId} AND rs.building_id IS NOT NULL "
                + "GROUP BY rs.building_id, b.building_code, b.name ORDER BY rs.building_id")
        java.util.List<java.util.Map<String, Object>> selectBuildingBoard(@Param("projectId") Long projectId);

        /** 户型去化分布：[{layout, total, sold}] */
        @Select("SELECT COALESCE(r.layout, '未知') AS layout, COUNT(*) AS total, "
                + "SUM(CASE WHEN rs.status IN ('SUBSCRIBED','CONTRACTED','FILED') THEN 1 ELSE 0 END) AS sold "
                + "FROM sale_room_status rs JOIN mdm_room r ON r.id = rs.room_id AND r.deleted = 0 "
                + "WHERE rs.deleted = 0 AND rs.project_id = #{projectId} "
                + "GROUP BY layout ORDER BY total DESC")
        java.util.List<java.util.Map<String, Object>> selectLayoutBoard(@Param("projectId") Long projectId);

        // ---------- 置业顾问业绩（顾问=认购顾问，或无认购时取合同中介） ----------

        /** 顾问认购：[{agentId, subCount, subAmount}] */
        @Select("SELECT s.agent_id AS agentId, COUNT(*) AS subCount, COALESCE(SUM(s.deposit),0) AS subAmount "
                + "FROM sale_subscription s WHERE s.deleted = 0 AND s.project_id = #{projectId} "
                + "AND s.status IN ('ACTIVE','CONVERTED','APPROVING') AND s.agent_id IS NOT NULL GROUP BY s.agent_id")
        java.util.List<java.util.Map<String, Object>> selectAdvisorSub(@Param("projectId") Long projectId);

        /** 顾问签约：[{agentId, conCount, conAmount}] */
        @Select("SELECT COALESCE(sub.agent_id, c.supplier_id) AS agentId, COUNT(*) AS conCount, COALESCE(SUM(c.contract_price),0) AS conAmount "
                + "FROM sale_contract c LEFT JOIN sale_subscription sub ON sub.id = c.subscription_id AND sub.deleted = 0 "
                + "WHERE c.deleted = 0 AND c.project_id = #{projectId} AND c.status IN ('ACTIVE','FILED') "
                + "AND COALESCE(sub.agent_id, c.supplier_id) IS NOT NULL GROUP BY COALESCE(sub.agent_id, c.supplier_id)")
        java.util.List<java.util.Map<String, Object>> selectAdvisorCon(@Param("projectId") Long projectId);

        /** 顾问回款：[{agentId, recCount, recAmount}] */
        @Select("SELECT COALESCE(sub.agent_id, c.supplier_id) AS agentId, COUNT(*) AS recCount, COALESCE(SUM(r.received_amount),0) AS recAmount "
                + "FROM sale_receipt r JOIN sale_contract c ON c.id = r.contract_id AND c.deleted = 0 "
                + "LEFT JOIN sale_subscription sub ON sub.id = c.subscription_id AND sub.deleted = 0 "
                + "WHERE r.deleted = 0 AND c.project_id = #{projectId} AND COALESCE(sub.agent_id, c.supplier_id) IS NOT NULL "
                + "GROUP BY COALESCE(sub.agent_id, c.supplier_id)")
        java.util.List<java.util.Map<String, Object>> selectAdvisorRec(@Param("projectId") Long projectId);

        // ---------- 销售目标实际完成值（按 metric 口径 + 周期） ----------

        /** 周期内签约金额(元) */
        @Select("SELECT COALESCE(SUM(contract_price),0) FROM sale_contract WHERE deleted = 0 AND project_id = #{projectId} "
                + "AND status IN ('ACTIVE','FILED') AND signed_at IS NOT NULL AND signed_at >= #{from} AND signed_at < #{to}")
        java.math.BigDecimal selectActualAmount(@Param("projectId") Long projectId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

        /** 周期内签约套数 */
        @Select("SELECT COUNT(*) FROM sale_contract WHERE deleted = 0 AND project_id = #{projectId} "
                + "AND status IN ('ACTIVE','FILED') AND signed_at IS NOT NULL AND signed_at >= #{from} AND signed_at < #{to}")
        long selectActualCount(@Param("projectId") Long projectId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

        /** 周期内签约面积(㎡) */
        @Select("SELECT COALESCE(SUM(r.area),0) FROM sale_contract c LEFT JOIN mdm_room r ON r.id = c.room_id AND r.deleted = 0 "
                + "WHERE c.deleted = 0 AND c.project_id = #{projectId} AND c.status IN ('ACTIVE','FILED') "
                + "AND c.signed_at IS NOT NULL AND c.signed_at >= #{from} AND c.signed_at < #{to}")
        java.math.BigDecimal selectActualArea(@Param("projectId") Long projectId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
    }
}
