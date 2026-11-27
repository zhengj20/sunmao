package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 集采下单（框架协议执行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_framework_order")
public class ProcFrameworkOrder extends BaseEntity {

    /** 下单编号（DD-时间戳） */
    private String orderCode;
    /** 战略集采协议 */
    private Long frameworkId;
    /** 业务项目 */
    private Long projectId;
    /** 采购内容 */
    private String content;
    /** 数量 */
    private BigDecimal quantity;
    /** 金额 */
    private BigDecimal amount;
    /** 下单日期 */
    private LocalDate orderDate;
    /** ORDERED/DELIVERED/CLOSED */
    private String status;
}
