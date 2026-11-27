package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主数据分发日志（表内无标准字段，普通实体）
 */
@Data
@TableName("mdm_distribute_log")
public class MdmDistributeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据类型：org/project/supplier/customer */
    private String dataType;
    /** 数据ID */
    private Long dataId;
    /** 目标域：cost/procurement/sales... */
    private String targetDomain;
    /** 分发状态，默认SUCCESS */
    private String status;
    /** 错误信息 */
    private String errorMsg;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
