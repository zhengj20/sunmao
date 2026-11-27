package com.reip.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统公告（sys_notice）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    /** 公告标题 */
    private String title;

    /** 公告正文 */
    private String content;

    /** 公告类型 NOTICE通知/ANNOUNCEMENT公告/MAINTENANCE维护 */
    private String noticeType;

    /** 置顶：0否 1是 */
    private Integer pinned;

    /** 发布时间 */
    private LocalDateTime publishAt;

    /** 发布人ID */
    private Long publisherId;

    /** 发布人姓名 */
    private String publisherName;

    /** PUBLISHED已发布/DRAFT草稿/ARCHIVED已归档 */
    private String status;
}
