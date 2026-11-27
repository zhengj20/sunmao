package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 供应商主数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_supplier")
public class MdmSupplier extends BaseEntity {
    /** 供应商编号（编码规则自动生成） */
    private String supplierCode;
    /** 供应商名称 */
    private String name;
    /** 简称 */
    private String shortName;
    /** 统一社会信用代码（唯一） */
    private String creditCode;
    /** 品类分类 */
    private Long categoryId;
    /** 分类：工程/材料/服务/设备 */
    private String classify;
    /** 评级 A/B/C/D */
    private String grade;
    /** 库别：OFFICIAL正式库/PRESELECT预选库/BLACKLIST黑名单 */
    private String libType;
    /** 法定代表人 */
    private String legalPerson;
    /** 注册资本（万元） */
    private BigDecimal registeredCapital;
    /** 联系人 */
    private String contactPerson;
    /** 联系电话 */
    private String contactPhone;
    /** 地址 */
    private String address;
    /** 履约评分 */
    private BigDecimal performanceScore;
    /** 拉黑原因 */
    private String blacklistReason;
    private String remark;
}
