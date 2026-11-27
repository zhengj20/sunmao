package com.reip.proc.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.framework.event.DomainEvent;
import com.reip.framework.event.EventHandler;
import com.reip.framework.event.EventPublisher;
import com.reip.proc.entity.ProcAward;
import com.reip.proc.entity.ProcEntry;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.entity.ProcRequirement;
import com.reip.proc.entity.ProcDirect;
import com.reip.proc.entity.ProcMisc;
import com.reip.proc.mapper.ProcMappers.AwardMapper;
import com.reip.proc.mapper.ProcMappers.DirectMapper;
import com.reip.proc.mapper.ProcMappers.EntryMapper;
import com.reip.proc.mapper.ProcMappers.MiscMapper;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.proc.mapper.ProcMappers.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招采模块工作流结果处理：监听 workflow.instance.finished
 * 审批通过→APPROVED（定标还需占规划/发通知/发事件）；驳回→回 DRAFT 可再编辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcWorkflowEventHandler implements EventHandler {

    public static final String TOPIC = "workflow.instance.finished";

    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;
    private final RequirementMapper requirementMapper;
    private final ProjectMapper projectMapper;
    private final EntryMapper entryMapper;
    private final AwardMapper awardMapper;
    private final DirectMapper directMapper;
    private final MiscMapper miscMapper;

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    @Transactional
    public void handle(DomainEvent event) {
        JsonNode node;
        try {
            node = objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            log.warn("[proc] 工作流事件负载解析失败：{}", event.getPayload(), e);
            return;
        }
        String bizType = node.path("bizType").asText(null);
        long bizId = node.path("bizId").asLong(0);
        boolean approved = node.path("approved").asBoolean(false);
        if (bizType == null || bizId <= 0) return;
        switch (bizType) {
            case "PROC_REQUIREMENT" -> handleRequirement(bizId, approved);
            case "PROC_PROJECT" -> handleProject(bizId, approved);
            case "PROC_ENTRY" -> handleEntry(bizId, approved);
            case "PROC_AWARD" -> {
                if (approved) {
                    awardApproved(bizId);
                } else {
                    awardRejected(bizId);
                }
            }
            case "PROC_DIRECT" -> handleDirect(bizId, approved);
            case "PROC_MISC" -> handleMisc(bizId, approved);
            default -> { }
        }
    }

    private void handleRequirement(Long id, boolean approved) {
        ProcRequirement r = requirementMapper.selectById(id);
        if (r == null) return;
        r.setStatus(approved ? "APPROVED" : "DRAFT");
        if (requirementMapper.updateById(r) == 0) {
            log.warn("[proc] 采购需求状态回写失败（并发冲突）id={}", id);
        }
    }

    private void handleProject(Long id, boolean approved) {
        ProcProject p = projectMapper.selectById(id);
        if (p == null) return;
        p.setStatus(approved ? "APPROVED" : "DRAFT");
        if (projectMapper.updateById(p) == 0) {
            log.warn("[proc] 招采立项状态回写失败（并发冲突）id={}", id);
        }
    }

    /** PROC_ENTRY 的 bizId = procProjectId：整体回写入围名单 + 项目进入 ENTRY */
    private void handleEntry(Long procProjectId, boolean approved) {
        List<ProcEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<ProcEntry>()
                .eq(ProcEntry::getProcProjectId, procProjectId));
        for (ProcEntry e : entries) {
            e.setStatus(approved ? "APPROVED" : "PENDING");
            if (entryMapper.updateById(e) == 0) {
                log.warn("[proc] 入围记录状态回写失败（并发冲突）id={}", e.getId());
            }
        }
        ProcProject project = projectMapper.selectById(procProjectId);
        if (project != null) {
            project.setStatus(approved ? "ENTRY" : "APPROVED");
            if (projectMapper.updateById(project) == 0) {
                log.warn("[proc] 招采立项入围状态回写失败（并发冲突）id={}", procProjectId);
            }
        }
    }

    /**
     * 定标审批通过：
     * 1) 定标 APPROVED、发中标通知（模拟 SENT）；
     * 2) 项目 AWARDED；
     * 3) 发布 procurement.award.confirmed 事件（成本模块监听自动生成合同草稿，字段名契约见 payload）。
     * 合约规划在途/已签约占用由成本合同生命周期（提交→审批）统一维护，招采侧不再重复占用。
     */
    private void awardApproved(Long awardId) {
        ProcAward award = awardMapper.selectById(awardId);
        if (award == null) return;
        ProcProject project = projectMapper.selectById(award.getProcProjectId());
        award.setStatus("APPROVED");
        award.setNoticeStatus("SENT"); // 模拟发送中标通知
        if (awardMapper.updateById(award) == 0) {
            log.warn("[proc] 定标状态回写失败（并发冲突）id={}", awardId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("procProjectId", award.getProcProjectId());
        payload.put("awardId", award.getId());
        payload.put("winnerSupplierId", award.getWinnerSupplierId());
        payload.put("winnerSupplierName", award.getWinnerSupplierName());
        payload.put("amount", award.getAwardAmount());
        if (project != null) {
            project.setStatus("AWARDED");
            if (projectMapper.updateById(project) == 0) {
                log.warn("[proc] 招采立项定标状态回写失败（并发冲突）id={}", project.getId());
            }
            payload.put("projectId", project.getProjectId());
            payload.put("planItemId", project.getPlanItemId());
            payload.put("title", project.getTitle());
        }
        eventPublisher.publish("procurement.award.confirmed", "PROC_AWARD", award.getId(), payload);
    }

    private void awardRejected(Long awardId) {
        ProcAward award = awardMapper.selectById(awardId);
        if (award == null) return;
        award.setStatus("DRAFT");
        if (awardMapper.updateById(award) == 0) {
            log.warn("[proc] 定标驳回回写失败（并发冲突）id={}", awardId);
        }
    }

    private void handleDirect(Long id, boolean approved) {
        ProcDirect d = directMapper.selectById(id);
        if (d == null) return;
        d.setStatus(approved ? "APPROVED" : "DRAFT");
        if (directMapper.updateById(d) == 0) {
            log.warn("[proc] 直接委托状态回写失败（并发冲突）id={}", id);
        }
    }

    private void handleMisc(Long id, boolean approved) {
        ProcMisc m = miscMapper.selectById(id);
        if (m == null) return;
        m.setStatus(approved ? "APPROVED" : "DRAFT");
        if (miscMapper.updateById(m) == 0) {
            log.warn("[proc] 零星采购状态回写失败（并发冲突）id={}", id);
        }
    }
}
