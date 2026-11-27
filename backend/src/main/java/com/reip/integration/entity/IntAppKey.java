package com.reip.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 调用方密钥（int_app_key）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_app_key")
public class IntAppKey extends BaseEntity {

    /** 调用方名称 */
    private String appName;

    /** 应用Key */
    private String appKey;

    /** 应用Secret（脱敏展示） */
    private String appSecret;

    /** 授权API编码，逗号分隔 */
    private String allowedApis;

    /** ENABLED/DISABLED */
    private String status;

    /** 过期时间 */
    private LocalDate expireDate;
}
