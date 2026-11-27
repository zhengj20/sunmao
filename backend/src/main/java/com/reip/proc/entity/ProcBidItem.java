package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 回标明细（技术/商务标分离打分项，父 ProcBid.techScore 按明细加权汇总）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_bid_item")
public class ProcBidItem extends BaseEntity {

    /** 所属回标 */
    private Long bidId;
    /** 明细编码（回标内唯一） */
    private String itemCode;
    /** 明细名称 */
    private String itemName;
    /** 技术得分 */
    private BigDecimal techScore;
    /** 商务得分 */
    private BigDecimal bizScore;
    /** 权重（默认1） */
    private BigDecimal weight;
    /** 是否无效 1是 0否（无效项不计入加权汇总） */
    private Integer isInvalid;
}
