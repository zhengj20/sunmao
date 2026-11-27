package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同台账
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_contract")
public class CostContract extends BaseEntity {

    /** 合同编号（HT 规则生成） */
    private String contractCode;
    /** 项目 */
    private Long projectId;
    /** 合约规划明细 */
    private Long planItemId;
    private String name;
    private Long supplierId;
    private String supplierName;
    /** 合同类型：GC总包/ZC分包/CL材料/FW服务 */
    private String contractType;
    /** 签约金额 */
    private BigDecimal signedAmount;
    private LocalDate signedDate;
    /** 承包范围 */
    private String scope;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 质保金比例% */
    private BigDecimal retentionRate;
    /** 已释放质保金 */
    private BigDecimal retentionReleased;
    /** 来源：MANUAL手动/AWARD招采定标生成 */
    private String source;
    /** 来源定标单 */
    private Long awardId;
    /** 状态：DRAFT/APPROVING/EFFECTIVE生效/SETTLED结算/CLOSED */
    private String status;
    /** 结算金额=签约+确认变更-扣款 */
    private BigDecimal settledAmount;
    private String remark;
}
