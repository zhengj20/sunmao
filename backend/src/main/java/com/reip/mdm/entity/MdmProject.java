package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目主数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_project")
public class MdmProject extends BaseEntity {
    /** 项目编号（编码规则自动生成，唯一） */
    private String projectCode;
    /** 项目名称 */
    private String name;
    /** 所属组织（区域） */
    private Long orgId;
    /** 城市 */
    private String city;
    /** 项目地址 */
    private String address;
    /** 合作模式：独资/合作/代建 */
    private String cooperationMode;
    /** 合作方 */
    private String partner;
    /** 拿地日期 */
    private LocalDate landAcquiredDate;
    /** 占地面积㎡ */
    private BigDecimal landArea;
    /** 计容面积㎡ */
    private BigDecimal farArea;
    /** 可售面积㎡ */
    private BigDecimal saleableArea;
    /** 容积率 */
    private BigDecimal plotRatio;
    /** 土地款（元） */
    private BigDecimal landCost;
    /** 状态：PREPARING筹备/CONSTRUCTION在建/DELIVERED交付/LIQUIDATION清算 */
    private String status;
    private String remark;
}
