package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户关联人
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_customer_relative")
public class MdmCustomerRelative extends BaseEntity {
    /** 所属客户 */
    private Long customerId;
    /** 关联人姓名 */
    private String name;
    /** 关联人证件号 */
    private String certNo;
    /** 关系：共同购买人/配偶/担保人 */
    private String relation;
    private String phone;
}
