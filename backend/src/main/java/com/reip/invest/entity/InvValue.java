package com.reip.invest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 货值台账（inv_value）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_value")
public class InvValue extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 业态：高层住宅/花园洋房/沿街商铺/地下车位 */
    private String productCategory;

    /** 面积（㎡） */
    private BigDecimal area;

    /** 价格版本：DEAL成交>REGISTERED备案>FORECAST预测 */
    private String priceVersion;

    /** 单价（元） */
    private BigDecimal price;

    /** 货值=面积×单价（保存时自动计算） */
    private BigDecimal totalValue;

    /** 供货时间 */
    private LocalDate supplyDate;

    /** 备注 */
    private String remark;
}
