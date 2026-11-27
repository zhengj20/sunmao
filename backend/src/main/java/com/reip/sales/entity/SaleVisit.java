package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 案场来访（判客保护期）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_visit")
public class SaleVisit extends BaseEntity {

    private String visitNo;
    private Long projectId;
    /** 客户姓名/手机号（判客键） */
    private String customerName;
    private String channel;
    private Long agentId;
    /** HIGH/MID/LOW */
    private String intentLevel;
    private LocalDateTime visitAt;
    /** REGISTERED/PROTECTED/LOST */
    private String status;
}
