package com.reip.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.cost.entity.*;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 成本模块 Mapper 聚合（模式同 PlatformMappers）
 */
public interface CostMappers {

    @Mapper
    interface TargetVersionMapper extends BaseMapper<CostTargetVersion> {}

    @Mapper
    interface TargetItemMapper extends BaseMapper<CostTargetItem> {}

    @Mapper
    interface PlanMapper extends BaseMapper<CostPlan> {}

    @Mapper
    interface PlanItemMapper extends BaseMapper<CostPlanItem> {

        /** 原子回写已签约占用（行锁安全，并发防超占） */
        @Update("UPDATE cost_plan_item SET contracted_amount = contracted_amount + #{amt} WHERE id = #{id}")
        int addContracted(@Param("id") Long id, @Param("amt") BigDecimal amt);

        /** 原子回写在途占用（提交加、结束减） */
        @Update("UPDATE cost_plan_item SET pending_amount = pending_amount + #{amt} WHERE id = #{id}")
        int addPending(@Param("id") Long id, @Param("amt") BigDecimal amt);
    }

    @Mapper
    interface ContractMapper extends BaseMapper<CostContract> {}

    @Mapper
    interface ContractAllocMapper extends BaseMapper<CostContractAlloc> {}

    @Mapper
    interface ChangeMapper extends BaseMapper<CostChange> {}

    @Mapper
    interface PaymentPlanMapper extends BaseMapper<CostPaymentPlan> {}

    @Mapper
    interface PaymentApplyMapper extends BaseMapper<CostPaymentApply> {}

    @Mapper
    interface InvoiceMapper extends BaseMapper<CostInvoice> {}

    @Mapper
    interface InvalidMapper extends BaseMapper<CostInvalid> {}

    @Mapper
    interface IndicatorMapper extends BaseMapper<CostIndicator> {}

    @Mapper
    interface DynamicSnapshotMapper extends BaseMapper<CostDynamicSnapshot> {}

    @Mapper
    interface SupplementMapper extends BaseMapper<CostSupplement> {}

    @Mapper
    interface TemplateMapper extends BaseMapper<CostContractTemplate> {}

    @Mapper
    interface MatterMapper extends BaseMapper<CostChangeMatter> {}

    /** MDM 主数据只读查询（项目编号/成本科目/供应商） */
    @Mapper
    interface MdmLookupMapper {

        @Select("SELECT project_code FROM mdm_project WHERE id = #{id} AND deleted = 0")
        String selectProjectCode(Long id);

        @Select("SELECT id, code, name FROM mdm_cost_account WHERE id = #{id} AND deleted = 0")
        AccountRef selectAccount(Long id);

        @Select("SELECT id, code, name FROM mdm_cost_account WHERE code = #{code} AND deleted = 0 LIMIT 1")
        AccountRef selectAccountByCode(@Param("code") String code);

        @Select("SELECT name FROM mdm_supplier WHERE id = #{id} AND deleted = 0")
        String selectSupplierName(Long id);
    }

    /** 科目引用（编码/名称带出） */
    @Data
    class AccountRef {
        private Long id;
        private String code;
        private String name;
    }
}
