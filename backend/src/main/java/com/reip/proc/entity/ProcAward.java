package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 定标（核心：联动成本占用合约规划，事件驱动生成合同草稿）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_award")
public class ProcAward extends BaseEntity {

    /** 定标编号（编码规则 DB） */
    private String awardCode;
    /** 招采立项 */
    private Long procProjectId;
    /** 中标供应商 */
    private Long winnerSupplierId;
    /** 中标供应商名称 */
    private String winnerSupplierName;
    /** 定标价 */
    private BigDecimal awardAmount;
    /** 预算对照（立项预算快照） */
    private BigDecimal budgetAmount;
    /** 偏差=定标价-预算 */
    private BigDecimal deviation;
    /** 偏差率% */
    private BigDecimal deviationPct;
    /** 定标控制校验 PASSED余量内/WARN超余量预警/BLOCKED阻断 */
    private String controlCheck;
    /** 推荐理由 */
    private String reason;
    /** UNSENT/SENT中标通知已发 */
    private String noticeStatus;
    /** 未中标通知状态 PENDING/SENT */
    private String loserNotifyStatus;
    /** 是否已生成合同草稿 1是 0否（成本模块回写） */
    private Integer contractGenerated;
    /** 生成的合同ID（成本模块回写） */
    private Long contractId;
    /** DRAFT/APPROVING/APPROVED/REJECTED(回DRAFT) */
    private String status;
}
