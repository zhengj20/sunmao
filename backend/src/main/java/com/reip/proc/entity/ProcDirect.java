package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 直接委托（阳光采购监控对象）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_direct")
public class ProcDirect extends BaseEntity {

    /** 业务项目 */
    private Long projectId;
    /** 委托事项标题 */
    private String title;
    /** 供应商 */
    private Long supplierId;
    /** 供应商名称（冗余展示） */
    private String supplierName;
    /** 委托金额 */
    private BigDecimal amount;
    /** 直接委托原因：垄断/应急/续约/其他 */
    private String directReason;
    private String reasonDesc;
    /** DRAFT/APPROVING/APPROVED/REJECTED(回DRAFT) */
    private String status;
}
