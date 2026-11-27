package com.reip.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同模板：模板文件 + 占位符，用于"根据模板生成合同"与在线预览
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_contract_template")
public class CostContractTemplate extends BaseEntity {

    /** 模板名称 */
    private String name;
    /** 适用合同类型：GC总包/ZC分包/CL材料/FW服务 */
    private String contractType;
    /** 原始文件名 */
    private String fileName;
    /** 存储相对路径（相对 uploads 目录） */
    private String filePath;
    /** 文件大小(bytes) */
    private Long fileSize;
    /** 占位符说明 JSON */
    private String placeholders;
    /** 描述 */
    private String description;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
}
