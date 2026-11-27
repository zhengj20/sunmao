package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目分期
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_phase")
public class MdmPhase extends BaseEntity {
    /** 所属项目 */
    private Long projectId;
    /** 分期编码 */
    private String phaseCode;
    /** 分期名称 */
    private String name;
    /** 计容面积㎡ */
    private BigDecimal farArea;
    /** 可售面积㎡ */
    private BigDecimal saleableArea;
    /** 状态 */
    private String status;
}
