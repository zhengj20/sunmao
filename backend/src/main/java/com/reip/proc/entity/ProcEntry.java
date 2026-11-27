package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 入围名单（含资格预审与考察）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_entry")
public class ProcEntry extends BaseEntity {

    /** 招采立项 */
    private Long procProjectId;
    /** 供应商 */
    private Long supplierId;
    /** 供应商名称（冗余展示） */
    private String supplierName;
    /** 资格预审结果 PASSED/FAILED */
    private String precheckResult;
    private String precheckRemark;
    /** 考察评分 */
    private BigDecimal inspectionScore;
    /** 考察结论 GOOD/PASS/BAD */
    private String inspectionConclusion;
    /** 考察附件 */
    private String inspectionAttachment;
    /** 1正式入围 0备选 */
    private Integer isCandidate;
    /** PENDING/APPROVING/APPROVED */
    private String status;
}
