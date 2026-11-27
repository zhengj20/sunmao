package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 零星采购（小金额采购，提交需满足比价家数≥阈值且报价一致）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_misc")
public class ProcMisc extends BaseEntity {

    /** 业务项目 */
    private Long projectId;
    /** 采购事项标题 */
    private String title;
    /** 采购金额 */
    private BigDecimal amount;
    /** 采购事由 */
    private String reason;
    /** 比价附件（如三家报价单） */
    private String compareAttachment;
    /** DRAFT/APPROVING/APPROVED */
    private String status;
}
