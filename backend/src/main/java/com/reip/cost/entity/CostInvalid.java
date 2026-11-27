package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 无效成本认定台账
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_invalid")
public class CostInvalid extends BaseEntity {

    /** 项目 */
    private Long projectId;
    /** 类型：超合同支付/返工损失/赔偿/罚款/其他 */
    private String invalidType;
    /** 认定金额 */
    private BigDecimal amount;
    private LocalDate occurredDate;
    private Long contractId;
    /** 责任部门 */
    private String responsibleDept;
    private String description;
    /** 状态：DRAFT/APPROVING/CONFIRMED */
    private String status;
}
