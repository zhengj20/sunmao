package com.reip.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 集成适配器（int_adapter_config）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_adapter_config")
public class IntAdapterConfig extends BaseEntity {

    /** 适配器编码 */
    private String adapterCode;

    /** 适配器名称 */
    private String adapterName;

    /** FINANCE财务/TAX税务/NETSIGN网签/MORTGAGE按揭/IM企微钉钉 */
    private String adapterType;

    /** 厂商：用友/金蝶/百望... */
    private String vendor;

    /** 参数化配置 JSON（城市/版本差异） */
    private String configJson;

    /** PUSH推送/PULL拉取/BOTH双向 */
    private String syncDirection;

    /** ENABLED/DISABLED */
    private String status;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 备注 */
    private String remark;
}
