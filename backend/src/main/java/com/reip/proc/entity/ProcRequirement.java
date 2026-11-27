package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购需求（需求→计划→立项→…全流程起点）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_requirement")
public class ProcRequirement extends BaseEntity {

    /** 需求编号（编码规则 CGXQ） */
    private String reqCode;
    /** 业务项目 */
    private Long projectId;
    /** 需求标题 */
    private String title;
    /** 成本科目 */
    private Long accountId;
    /** 采购品类 */
    private Long categoryId;
    /** 来源合约规划明细（余量校验） */
    private Long planItemId;
    /** 需求描述 */
    private String description;
    /** 预估金额 */
    private BigDecimal estimateAmount;
    /** 期望到货/开工时间 */
    private LocalDate expectDate;
    /** NORMAL普通/URGENT紧急 */
    private String urgency;
    /** 是否已有集采覆盖 1是 0否 */
    private Integer coveredByFramework;
    /** DRAFT/APPROVING/APPROVED/MERGED已并入计划/REJECTED(回DRAFT) */
    private String status;
    private String remark;
}
