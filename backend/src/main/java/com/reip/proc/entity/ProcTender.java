package com.reip.proc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发标管理（每个立项一个发标）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_tender")
public class ProcTender extends BaseEntity {

    /** 发标编号（编码规则 FB） */
    private String tenderCode;
    /** 招采立项 */
    private Long procProjectId;
    /** 标书版本 */
    private String docVersion;
    /** 保证金 */
    private BigDecimal depositAmount;
    /** 保证金已退还 1是 0否 */
    private Integer depositRefunded;
    /** 发标时间 */
    private LocalDateTime publishTime;
    /** 回标截止时间 */
    private LocalDateTime deadline;
    /** 答疑纪要 JSON 数组 [{question,answer,time}] */
    private String clarifyNotes;
    /** 标书文件附件 */
    private String docAttachment;
    /** 答疑纪要附件 */
    private String clarifyAttachment;
    /** PUBLISHED发标中/CLARIFY答疑/OPENED已开标/CLOSED */
    private String status;
}
