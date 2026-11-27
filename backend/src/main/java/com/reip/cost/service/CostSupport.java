package com.reip.cost.service;

import com.reip.common.exception.BizException;
import com.reip.cost.mapper.CostMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 成本模块公共支撑：编码规则上下文 / MDM 带出
 */
@Component
@RequiredArgsConstructor
public class CostSupport {

    private final CostMappers.MdmLookupMapper mdmMapper;

    /** 编码规则上下文（需含 projectCode，从 mdm_project 查出） */
    public Map<String, String> codeCtx(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        String projectCode = mdmMapper.selectProjectCode(projectId);
        if (projectCode == null) throw new BizException("项目不存在：" + projectId);
        return Map.of("projectCode", projectCode);
    }

    /** 校验并带出科目编码/名称 */
    public CostMappers.AccountRef account(Long accountId) {
        if (accountId == null) throw new BizException("请选择成本科目");
        CostMappers.AccountRef ref = mdmMapper.selectAccount(accountId);
        if (ref == null) throw new BizException("成本科目不存在：" + accountId);
        return ref;
    }

    /** 供应商名称带出（可空） */
    public String supplierName(Long supplierId) {
        return supplierId == null ? null : mdmMapper.selectSupplierName(supplierId);
    }

    /** 根据科目编码查找科目 */
    public CostMappers.AccountRef accountByCode(String accountCode) {
        if (accountCode == null || accountCode.isBlank()) throw new BizException("科目编码不能为空");
        return mdmMapper.selectAccountByCode(accountCode);
    }

    /** 金额空值兜底 */
    public static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
