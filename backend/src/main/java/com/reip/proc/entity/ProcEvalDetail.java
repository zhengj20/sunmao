package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 专家评分明细（留痕，一位专家对一家回标一行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_eval_detail")
public class ProcEvalDetail extends BaseEntity {

    /** 所属评标 */
    private Long evaluationId;
    /** 回标 */
    private Long bidId;
    /** 供应商名称（冗余展示） */
    private String supplierName;
    /** 专家姓名 */
    private String expertName;
    /** 技术评分 */
    private BigDecimal techScore;
    /** 商务评分（缺省用价格分） */
    private BigDecimal bizScore;
    /** 总分（评标完成时回写） */
    private BigDecimal totalScore;
    /** 排名（评标完成时回写） */
    private Integer rankNo;
}
