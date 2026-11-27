package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 变更事项申请：签证/设计变更的上游"申请/背景"。一个事项可关联多个签证与变更(1:N)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_change_matter")
public class CostChangeMatter extends BaseEntity {

    /** 事项编号（SM 规则生成） */
    private String matterCode;
    /** 项目 */
    private Long projectId;
    /** 关联合同（可空，事项可为项目级） */
    private Long contractId;
    /** 无合同挂科目（可空） */
    private Long accountId;
    /** 事项类型：SJBG设计变更类/GCQZ工程签证类/ZH综合 */
    private String matterType;
    /** 事项名称 */
    private String title;
    /** 原因归类 */
    private String reasonCategory;
    /** 事项背景/原因（为什么发生） */
    private String description;
    /** 预估金额合计（申请口径） */
    private BigDecimal estimateTotal;
    /** 状态：DRAFT草稿/APPROVED已批准/CLOSED已关闭 */
    private String status;
    /** 申请人 */
    private Long appliedBy;
    /** 申请日期 */
    private LocalDate applyDate;
    /** 备注 */
    private String remark;
}
