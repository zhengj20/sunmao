package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同补充协议
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_supplement")
public class CostSupplement extends BaseEntity {

    /** 补充协议编号 */
    private String supplementCode;
    /** 关联合同 */
    private Long contractId;
    /** 项目 */
    private Long projectId;
    /** 协议标题 */
    private String title;
    /** 补充金额（可正可负） */
    private BigDecimal amount;
    /** 协议日期 */
    private LocalDate supplementDate;
    /** 协议说明 */
    private String description;
    /** 状态：DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;
    /** 备注 */
    private String remark;
}
