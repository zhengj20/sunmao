package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 战略集采协议（框架单价表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_framework")
public class ProcFramework extends BaseEntity {

    /** 协议编号（ZC-时间戳） */
    private String frameworkCode;
    /** 协议名称 */
    private String name;
    /** 供应商 */
    private Long supplierId;
    /** 供应商名称（冗余展示） */
    private String supplierName;
    /** 采购品类 */
    private Long categoryId;
    /** 框架单价表 JSON 数组 [{name,unit,unitPrice}] */
    private String unitPriceTable;
    /** 有效期起 */
    private LocalDate validFrom;
    /** 有效期止 */
    private LocalDate validTo;
    /** ACTIVE/EXPIRED */
    private String status;
    private String remark;
}
