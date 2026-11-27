package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应商资质证照
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_supplier_qualification")
public class MdmSupplierQualification extends BaseEntity {
    /** 所属供应商 */
    private Long supplierId;
    /** 证照类型：营业执照/资质证书/安全生产许可证 */
    private String qualType;
    /** 证照编号 */
    private String qualNo;
    /** 发证日期 */
    private LocalDate issueDate;
    /** 到期日期 */
    private LocalDate expireDate;
    /** 影像附件ID */
    private Long fileId;
    /** 1=OCR已识别 */
    private Integer ocrStatus;

    /** 展示用：供应商名称 */
    private transient String supplierName;
    /** 展示用：供应商编号 */
    private transient String supplierCode;
}
