package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 合同金额分摊科目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_contract_alloc")
public class CostContractAlloc extends BaseEntity {

    /** 所属合同 */
    private Long contractId;
    /** 成本科目 */
    private Long accountId;
    /** 科目名称（从科目树带出） */
    private String accountName;
    /** 分摊金额 */
    private BigDecimal amount;
}
