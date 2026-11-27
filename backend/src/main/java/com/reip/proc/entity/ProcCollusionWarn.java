package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 围串标预警（无标准字段，普通实体）
 * rule_code：SAME_IP同IP / PRICE_LADDER规律梯度 / SHAREHOLDER股东关联
 */
@Data
@TableName("proc_collusion_warn")
public class ProcCollusionWarn implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 招采立项 */
    private Long procProjectId;
    /** 预警规则编码 */
    private String ruleCode;
    /** 预警详情 */
    private String detail;
    /** LOW/MEDIUM/HIGH */
    private String riskLevel;
    private LocalDateTime createdAt;
}
