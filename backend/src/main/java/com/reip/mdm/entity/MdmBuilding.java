package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 楼栋
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_building")
public class MdmBuilding extends BaseEntity {
    /** 所属项目 */
    private Long projectId;
    /** 所属分期 */
    private Long phaseId;
    /** 栋号 */
    private String buildingCode;
    /** 楼栋名称 */
    private String name;
    /** 地上层数 */
    private Integer floors;
    /** 地下层数 */
    private Integer undergroundFloors;
    /** 单元数 */
    private Integer units;
    /** 总面积㎡ */
    private BigDecimal totalArea;
    /** 状态：PLANNED规划/CONSTRUCTING在建/COMPLETED竣工 */
    private String status;
}
