package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回标记录（技术标/商务报价分离，围串标检测数据源）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_bid")
public class ProcBid extends BaseEntity {

    /** 发标 */
    private Long tenderId;
    /** 招采立项 */
    private Long procProjectId;
    /** 供应商 */
    private Long supplierId;
    /** 供应商名称（冗余展示） */
    private String supplierName;
    /** 回标时间 */
    private LocalDateTime bidTime;
    /** 技术标得分（专家评分均值，评标回写） */
    private BigDecimal techScore;
    /** 商务报价 */
    private BigDecimal priceAmount;
    /** 回标IP（围串标预警：同IP不同供应商） */
    private String bidIp;
    /** 符合性审查 PASSED/FAILED */
    private String complianceResult;
    /** 偏差说明 */
    private String deviationNotes;
    /** 不平衡报价标记 1是 0否（清标检测：报价>均值×1.3） */
    private Integer unbalancedFlag;
    /** SUBMITTED/OPENED/WINNER/LOSER/INVALID废标 */
    private String bidStatus;
}
