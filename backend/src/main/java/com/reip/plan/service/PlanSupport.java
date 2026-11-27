package com.reip.plan.service;

import com.reip.common.exception.BizException;
import com.reip.plan.entity.PlanTask;
import com.reip.plan.mapper.PlanMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计划模块公共支撑：金额兜底 / 天数差 / 预警颜色计算 / 前置依赖拆分 / 编码上下文
 */
@Component
@RequiredArgsConstructor
public class PlanSupport {

    public static final int MILESTONE = 1;

    private final PlanMappers.MdmLookupMapper mdm;

    public static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 编码规则上下文（需含 projectCode） */
    public Map<String, String> codeCtx(Long projectId) {
        if (projectId == null) throw new BizException("请选择项目");
        String projectCode = mdm.selectProjectCode(projectId);
        if (projectCode == null) throw new BizException("项目不存在：" + projectId);
        return Map.of("projectCode", projectCode);
    }

    public String orgName(Long orgId) {
        return orgId == null ? null : mdm.selectOrgName(orgId);
    }

    /** 滞后天数：基线完成日早于今天且未完成的差距（>0 表示滞后） */
    public static long overdueDays(PlanTask t, LocalDate today) {
        if (t.getBaselineEnd() == null) return 0;
        if ("COMPLETED".equals(t.getStatus())) return 0;
        long d = ChronoUnit.DAYS.between(t.getBaselineEnd(), today);
        return Math.max(0, d);
    }

    /**
     * 预警颜色：完成=GREEN；滞后<=yellowDays=YELLOW；>yellowDays=RED
     */
    public static String alertColor(PlanTask t, LocalDate today, int yellowDays) {
        if ("COMPLETED".equals(t.getStatus())) return "GREEN";
        long d = overdueDays(t, today);
        if (d <= 0) return "GREEN";
        return d <= yellowDays ? "YELLOW" : "RED";
    }

    /** 拆分前置依赖ID串（逗号分隔） */
    public static List<Long> preIds(String preTaskIds) {
        List<Long> ids = new ArrayList<>();
        if (preTaskIds == null || preTaskIds.isBlank()) return ids;
        for (String s : preTaskIds.split(",")) {
            String x = s.trim();
            if (x.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(x));
            } catch (NumberFormatException ignore) {
            }
        }
        return ids;
    }

    public static boolean isMilestone(PlanTask t) {
        return t.getIsMilestone() != null && t.getIsMilestone() == MILESTONE;
    }
}
