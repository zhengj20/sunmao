package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户跟进记录（客户池 + 跟进轨迹 + 跟进提醒）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_follow")
public class SaleFollow extends BaseEntity {

    private Long projectId;
    private Long customerId;
    /** 客户姓名/手机号（判客键） */
    private String customerName;
    /** 电话/到访/微信/短信 */
    private String followMethod;
    /** 跟进内容 */
    private String followContent;
    /** HIGH/MID/LOW */
    private String intentLevel;
    /** 下次跟进时间 */
    private LocalDate nextFollowDate;
    /** FOLLOWING跟进中/DEAL已成交/LOST已流失 */
    private String dealStatus;
    /** 置业顾问 */
    private Long followBy;
    /** 跟进时间 */
    private LocalDateTime followAt;

    /** 展示用：跟进人姓名 */
    private transient String followByName;
}
