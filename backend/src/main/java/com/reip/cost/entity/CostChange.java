package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 变更管理（两阶段：估价→确认）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_change")
public class CostChange extends BaseEntity {

    /** 变更单编号（BG 规则生成） */
    private String changeCode;
    /** 项目 */
    private Long projectId;
    /** 关联合同（无合同变更为空） */
    private Long contractId;
    /** 所属事项申请（1事项→N变更） */
    private Long matterId;
    /** 无合同变更挂科目 */
    private Long accountId;
    /** 变更类型：SJBG设计变更/GCQZ工程签证/QSSY洽商 */
    private String changeType;
    private String title;
    /** 原因归类 */
    private String reasonCategory;
    private String description;
    /** 估价金额（一阶段） */
    private BigDecimal estimateAmount;
    /** 确认金额（二阶段） */
    private BigDecimal confirmedAmount;
    /** 扣款金额 */
    private BigDecimal deductAmount;
    /** 状态：ESTIMATING估价中/ESTIMATE_APPROVING/CONFIRMING待确认/APPROVING确认审批中/CONFIRMED已确认/REJECTED */
    private String status;
    /** 是否有合同：1是 0否 */
    private Integer requireContract;
    /** 附件 JSON */
    private String attachments;
    private String remark;
}
