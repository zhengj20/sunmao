package com.reip.portal.service;

import com.reip.framework.auth.UserContext;
import com.reip.platform.entity.SysNotice;
import com.reip.platform.service.SysNoticeService;
import com.reip.portal.mapper.PortalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户工作台聚合：待办 / 公告 / 集团经营指标 / 我的草稿 / 最新事件
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    private final PortalMapper portalMapper;
    private final SysNoticeService sysNoticeService;

    private static final DateTimeFormatter NOTICE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> workbench() {
        Long userId = UserContext.require().getUserId();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("projectCount", portalMapper.countConstructionProjects());
        stats.put("contractAmount", portalMapper.sumContractAmount());
        stats.put("paymentAmount", portalMapper.sumPaymentAmount());
        stats.put("dynamicCost", portalMapper.sumLatestDynamicCost());
        stats.put("dynamicProfit", portalMapper.sumLatestDynamicProfit());
        stats.put("saleValue", portalMapper.sumSaleValue());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todoCount", portalMapper.countTodo(userId));
        result.put("notices", notices());
        result.put("stats", stats);
        result.put("myDrafts", portalMapper.selectMyDrafts(userId));
        result.put("recentEvents", portalMapper.selectRecentEvents());
        return result;
    }

    /** 门户公告：优先读 sys_notice（置顶优先 + 已发布，取最近8条），无数据则回退内置3条 */
    private List<Map<String, Object>> notices() {
        List<SysNotice> dbNotices = sysNoticeService.listForPortal(8);
        if (!dbNotices.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>(dbNotices.size());
            for (SysNotice n : dbNotices) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.getId());
                m.put("title", n.getTitle());
                m.put("content", n.getContent());
                m.put("publishTime", n.getPublishAt() == null ? "" : n.getPublishAt().format(NOTICE_TIME_FMT));
                result.add(m);
            }
            return result;
        }
        return List.of(
                notice(1L, "关于开展2026年三季度投资测算复核的通知",
                        "各城市公司请于9月20日前完成在途项目投资测算版本复核，并提交集团投决会审议。",
                        "2026-09-01 09:00:00"),
                notice(2L, "9月资金计划申报截止提醒",
                        "本月资金计划（DRAFT版本）申报截止日为9月25日，请各项目财务提前完成汇总与提交。",
                        "2026-09-02 10:30:00"),
                notice(3L, "动态成本事件驱动机制上线公告",
                        "自2026年9月起，动态成本快照调整为事件驱动自动重算，成本确认后实时联动刷新动态利润。",
                        "2026-09-03 16:00:00"));
    }

    private Map<String, Object> notice(Long id, String title, String content, String publishTime) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("title", title);
        n.put("content", content);
        n.put("publishTime", publishTime);
        return n;
    }
}
