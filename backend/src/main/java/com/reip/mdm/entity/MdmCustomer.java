package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户主数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_customer")
public class MdmCustomer extends BaseEntity {
    /** 客户编号（编码规则自动生成） */
    private String customerCode;
    /** 客户名称 */
    private String name;
    /** 证件类型：ID_CARD身份证/PASSPORT护照/BIZ_LICENSE营业执照 */
    private String certType;
    /** 证件号 */
    private String certNo;
    /** 客户类型：PERSON个人/ENTERPRISE企业 */
    private String customerType;
    private String mobile;
    private String address;
    /** 归属置业顾问（二期销售） */
    private Long advisorUserId;
    private String remark;
}
