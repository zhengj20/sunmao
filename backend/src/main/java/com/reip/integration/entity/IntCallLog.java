package com.reip.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集成调用日志（int_call_log，无标准字段→普通实体）
 */
@Data
@TableName("int_call_log")
public class IntCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 适配器编码 */
    private String adapterCode;

    /** API编码 */
    private String apiCode;

    /** IN/OUT */
    private String direction;

    /** 请求摘要 */
    private String requestDigest;

    /** 响应摘要 */
    private String responseDigest;

    /** 1成功 0失败 */
    private Integer success;

    /** 耗时ms */
    private Long costMs;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createdAt;
}
