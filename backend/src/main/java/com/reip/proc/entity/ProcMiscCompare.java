package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 零星采购比价明细（提交硬校验：家数≥阈值且各家报价一致）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_misc_compare")
public class ProcMiscCompare extends BaseEntity {

    /** 所属零星采购 */
    private Long miscId;
    /** 供应商名称 */
    private String supplierName;
    /** 报价金额 */
    private BigDecimal price;
}
