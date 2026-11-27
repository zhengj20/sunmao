package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 房间（房间生成器产出）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_room")
public class MdmRoom extends BaseEntity {
    /** 所属项目 */
    private Long projectId;
    /** 所属楼栋 */
    private Long buildingId;
    /** 房间唯一编号 */
    private String roomCode;
    /** 楼层（负数为地下层） */
    private Integer floorNo;
    /** 单元号 */
    private Integer unitNo;
    /** 户室号 */
    private String roomNo;
    /** 户型 */
    private String layout;
    /** 朝向 */
    private String orientation;
    /** 建筑面积㎡ */
    private BigDecimal area;
    /** 套内面积㎡ */
    private BigDecimal innerArea;
    /** 业态 */
    private Long productTypeId;
    /** 1可售 0不可售 */
    private Integer saleable;
    /** 销控状态：INIT初始/LOCKED锁定/SOLD售出（销售域回写） */
    private String saleStatus;

    /** 展示用：楼栋名称 */
    private transient String buildingName;
    /** 展示用：栋号 */
    private transient String buildingCode;
    /** 展示用：项目名称 */
    private transient String projectName;
    /** 展示用：项目编号 */
    private transient String projectCode;
}
