package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售渠道（中介/分销/线上/老带新/自然）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_channel")
public class SaleChannel extends BaseEntity {

    private Long projectId;
    /** 渠道名称 */
    private String channelName;
    /** 中介/分销/线上/老带新/自然来访 */
    private String channelType;
    /** 对接人 */
    private String contactName;
    /** 佣金率(0~1) */
    private BigDecimal commissionRate;
    /** ACTIVE/INACTIVE */
    private String status;
}
