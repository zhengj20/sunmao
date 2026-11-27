package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 招采立项（注意与业务项目 mdm_project 区分：本表是招采立项单）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_project")
public class ProcProject extends BaseEntity {

    /** 立项编号（编码规则 CGXM） */
    private String projectCode;
    /** 业务项目（mdm_project.id） */
    private Long projectId;
    /** 标的名称 */
    private String title;
    /** 预算（来源目标成本/合约规划） */
    private BigDecimal budgetAmount;
    /** 关联合约规划明细（定标控制余量校验） */
    private Long planItemId;
    /** 资金来源 */
    private String fundSource;
    /** 采购方式：PUBLIC_BID/INVITE_BID/COMPETITIVE/INQUIRY/DIRECT/FRAMEWORK */
    private String procureMethod;
    /** 方式选择理由（直接委托等越级方式必填） */
    private String methodReason;
    /** 工期（天） */
    private Integer periodDays;
    /** DRAFT/APPROVING/APPROVED/ENTRY入围中/TENDERING发标中/EVALUATING评标中/AWARDED已定标/CLOSED */
    private String status;
    private String remark;
}
