package com.reip.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_employee")
public class MdmEmployee extends BaseEntity {
    /** 关联登录账号 */
    private Long userId;
    /** 工号 */
    private String empNo;
    /** 姓名 */
    private String realName;
    /** 所属组织 */
    private Long orgId;
    /** 岗位 */
    private String position;
    /** 职务级别 */
    private String jobLevel;
    private String mobile;
    private String email;
    /** 证件号（脱敏展示） */
    private String idCard;
    /** 入职日期 */
    private LocalDate entryDate;
    /** 离职日期 */
    private LocalDate leaveDate;
    /** 状态：IN_SERVICE在职/TRANSFER调动/LEAVE离职 */
    private String status;

    /** 展示用：关联登录账号姓名 */
    private transient String userName;
    /** 展示用：所属组织名称 */
    private transient String orgName;
}
