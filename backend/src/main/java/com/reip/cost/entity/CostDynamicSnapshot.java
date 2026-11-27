package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动态成本月度快照（事件驱动重算，不可修改；无标准字段故为普通实体）
 */
@Data
@TableName("cost_dynamic_snapshot")
public class CostDynamicSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 项目 */
    private Long projectId;
    /** 快照月份 yyyy-MM */
    private String snapMonth;
    /** 目标成本 */
    private BigDecimal targetTotal;
    /** 已签合同 */
    private BigDecimal contractedTotal;
    /** 已确认变更 */
    private BigDecimal confirmedChange;
    /** 预估变更 */
    private BigDecimal estimatedChange;
    /** 非合约支出 */
    private BigDecimal nonContract;
    /** 余量（可为负） */
    private BigDecimal remaining;
    /** 动态成本合计 */
    private BigDecimal dynamicTotal;
    /** 科目级构成 JSON */
    private String detailJson;
    private LocalDateTime createdAt;
}
