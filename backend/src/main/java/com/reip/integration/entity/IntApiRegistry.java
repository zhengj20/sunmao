package com.reip.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开放API注册（int_api_registry）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_api_registry")
public class IntApiRegistry extends BaseEntity {

    /** API编码 */
    private String apiCode;

    /** API名称 */
    private String apiName;

    /** 外部系统地址/路径 */
    private String apiPath;

    /** 用途说明 */
    private String purpose;

    /** 限流QPS */
    private Integer rateLimitQps;

    /** 责任人 */
    private Long ownerUserId;

    /** ENABLED/DISABLED */
    private String status;
}
