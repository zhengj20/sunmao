package com.reip.sales.service;

import com.reip.common.exception.BizException;
import com.reip.sales.mapper.SaleMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 销售模块公共支撑：编码规则上下文 / MDM 名称带出 / 金额兜底
 */
@Component
@RequiredArgsConstructor
public class SaleSupport {

    private final SaleMappers.MdmLookupMapper mdm;

    /** 编码规则上下文（需含 projectCode，从 mdm_project 查出） */
    public Map<String, String> codeCtx(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        String projectCode = mdm.selectProjectCode(projectId);
        if (projectCode == null) throw new BizException("项目不存在：" + projectId);
        return Map.of("projectCode", projectCode);
    }

    public String roomCode(Long roomId) {
        return roomId == null ? null : mdm.selectRoomCode(roomId);
    }

    public String customerName(Long customerId) {
        return customerId == null ? null : mdm.selectCustomerName(customerId);
    }

    public String agentName(Long agentId) {
        return agentId == null ? null : mdm.selectUserName(agentId);
    }

    public static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
