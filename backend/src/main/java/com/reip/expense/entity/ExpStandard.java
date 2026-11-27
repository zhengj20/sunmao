package com.reip.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费控标准（职级×城市等级×类型）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_standard")
public class ExpStandard extends BaseEntity {

    /** 职级(E5/E6/E7/M3/M4/M5) */
    private String level;
    /** 城市等级(一线/二线/三线/全部) */
    private String cityLevel;
    /** TRAVEL/HOST/COMMS/CAR */
    private String type;
    /** 限额标准 */
    private BigDecimal limitAmount;
}
