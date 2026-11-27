package com.reip.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 来账（银企直联 / 手机短信）：收款信息登记 + 自动/手动匹配到合同·房间 + 生成实收
 * source：BANK银企直联 / SMS手机短信
 * matchStatus：PENDING待匹配 / MATCHED已匹配(已生成实收) / REJECTED已排除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_incoming")
public class SaleIncoming extends BaseEntity {

    private String incomingNo;
    private Long projectId;
    /** BANK银企直联 / SMS手机短信 */
    private String source;
    /** 付款人(客户名/账号) */
    private String payerName;
    private String payerAccount;
    /** 来账金额 */
    private BigDecimal amount;
    /** 银行流水号 / 短信流水号 */
    private String bankSerial;
    /** 到账时间 */
    private LocalDateTime receivedAt;
    /** 匹配到的合同 */
    private Long contractId;
    private Long roomId;
    private String roomCode;
    /** PENDING待匹配/MATCHED已匹配/REJECTED已排除 */
    private String matchStatus;
    /** 生成的首笔实收单 */
    private Long receiptId;
    /** 匹配人 */
    private Long matchBy;
    private LocalDateTime matchAt;
    private String remark;

    /** 展示用：合同号 */
    private transient String contractNo;
    /** 展示用：客户名 */
    private transient String customerName;
}
