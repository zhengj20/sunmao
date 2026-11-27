package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.cost.entity.CostChange;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostContractAlloc;
import com.reip.cost.entity.CostDynamicSnapshot;
import com.reip.cost.entity.CostInvalid;
import com.reip.cost.entity.CostTargetVersion;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 动态成本：重算落当月快照（幂等 upsert）/ 最新快照与月度趋势
 */
@Service
@RequiredArgsConstructor
public class CostDynamicService {

    /** 未分摊科目桶 */
    private static final Long UNALLOCATED = -1L;

    private final CostMappers.DynamicSnapshotMapper snapshotMapper;
    private final CostMappers.TargetVersionMapper versionMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CostMappers.ContractAllocMapper allocMapper;
    private final CostMappers.ChangeMapper changeMapper;
    private final CostMappers.InvalidMapper invalidMapper;
    private final CostMappers.MdmLookupMapper mdmMapper;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 重算并落当月快照：
     * 目标=最新 APPROVED/LOCKED 版本合计；已签=EFFECTIVE+SETTLED 签约额；
     * 确认变更/预估变更按变更台账；余量=目标-已签-确认-预估（可为负）；动态=已签+确认+预估
     */
    @Transactional
    public CostDynamicSnapshot refresh(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        // 目标成本：最新 APPROVED/LOCKED 版本
        CostTargetVersion target = versionMapper.selectOne(new LambdaQueryWrapper<CostTargetVersion>()
                .eq(CostTargetVersion::getProjectId, projectId)
                .in(CostTargetVersion::getStatus, "APPROVED", "LOCKED")
                .orderByDesc(CostTargetVersion::getId).last("LIMIT 1"));
        BigDecimal targetTotal = target == null ? BigDecimal.ZERO : CostSupport.nvl(target.getTotalAmount());
        // 已签合同
        List<CostContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<CostContract>()
                .eq(CostContract::getProjectId, projectId)
                .in(CostContract::getStatus, "EFFECTIVE", "SETTLED"));
        BigDecimal contracted = contracts.stream().map(c -> CostSupport.nvl(c.getSignedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 变更
        List<CostChange> changes = changeMapper.selectList(new LambdaQueryWrapper<CostChange>()
                .eq(CostChange::getProjectId, projectId));
        BigDecimal confirmed = BigDecimal.ZERO, estimated = BigDecimal.ZERO;
        for (CostChange c : changes) {
            if ("CONFIRMED".equals(c.getStatus())) {
                confirmed = confirmed.add(CostSupport.nvl(c.getConfirmedAmount())
                        .subtract(CostSupport.nvl(c.getDeductAmount())));
            } else if (isInFlight(c.getStatus())) {
                estimated = estimated.add(CostSupport.nvl(c.getEstimateAmount()));
            }
        }
        // 非合约支出：已确认的无效成本
        List<CostInvalid> invalids = invalidMapper.selectList(new LambdaQueryWrapper<CostInvalid>()
                .eq(CostInvalid::getProjectId, projectId).eq(CostInvalid::getStatus, "CONFIRMED"));
        BigDecimal nonContract = invalids.stream()
                .map(i -> CostSupport.nvl(i.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = targetTotal.subtract(contracted).subtract(confirmed).subtract(estimated).subtract(nonContract);
        BigDecimal dynamicTotal = contracted.add(confirmed).add(estimated).add(nonContract);
        String snapMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        CostDynamicSnapshot snap = new CostDynamicSnapshot();
        snap.setProjectId(projectId);
        snap.setSnapMonth(snapMonth);
        snap.setTargetTotal(targetTotal);
        snap.setContractedTotal(contracted);
        snap.setConfirmedChange(confirmed);
        snap.setEstimatedChange(estimated);
        snap.setNonContract(nonContract);
        snap.setRemaining(remaining);
        snap.setDynamicTotal(dynamicTotal);
        snap.setDetailJson(buildDetailJson(contracts, changes));
        snap.setCreatedAt(LocalDateTime.now());
        // 幂等 upsert：同项目同月先删后插
        snapshotMapper.delete(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .eq(CostDynamicSnapshot::getSnapMonth, snapMonth));
        snapshotMapper.insert(snap);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("dynamicTotal", dynamicTotal);
        payload.put("snapMonth", snapMonth);
        payload.put("targetTotal", targetTotal);
        eventPublisher.publish("cost.dynamic.updated", "COST_DYNAMIC", snap.getId(), payload);
        return snap;
    }

    /** 最新快照+历史月度列表 */
    public Map<String, Object> latest(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        List<CostDynamicSnapshot> all = snapshotMapper.selectList(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .orderByDesc(CostDynamicSnapshot::getSnapMonth));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latest", all.isEmpty() ? null : all.get(0));
        result.put("history", all);
        return result;
    }

    /** 月度趋势 */
    public List<CostDynamicSnapshot> history(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        return snapshotMapper.selectList(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .orderByAsc(CostDynamicSnapshot::getSnapMonth));
    }

    /**
     * 成本预警中心：检查项目成本预警状态
     * 预警规则：
     * 1. 动态成本超目标成本
     * 2. 余量不足（低于目标成本的10%）
     * 3. 变更率超标（超过5%）
     */
    public Map<String, Object> warningCenter(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        List<Map<String, Object>> warnings = new ArrayList<>();
        // 获取最新快照
        CostDynamicSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .orderByDesc(CostDynamicSnapshot::getSnapMonth).last("LIMIT 1"));
        if (latest != null) {
            BigDecimal targetTotal = CostSupport.nvl(latest.getTargetTotal());
            BigDecimal dynamicTotal = CostSupport.nvl(latest.getDynamicTotal());
            BigDecimal remaining = CostSupport.nvl(latest.getRemaining());
            // 规则1：动态成本超目标成本
            if (dynamicTotal.compareTo(targetTotal) > 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("type", "OVER_TARGET");
                warning.put("level", "HIGH");
                warning.put("message", String.format("动态成本 %s 元已超过目标成本 %s 元，超出 %s 元",
                        dynamicTotal, targetTotal, dynamicTotal.subtract(targetTotal)));
                warnings.add(warning);
            }
            // 规则2：余量不足
            if (targetTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingRate = remaining.multiply(BigDecimal.valueOf(100))
                        .divide(targetTotal, 2, RoundingMode.HALF_UP);
                if (remainingRate.compareTo(new BigDecimal("10")) < 0) {
                    Map<String, Object> warning = new LinkedHashMap<>();
                    warning.put("type", "LOW_REMAINING");
                    warning.put("level", remaining.compareTo(BigDecimal.ZERO) < 0 ? "HIGH" : "MEDIUM");
                    warning.put("message", String.format("余量 %s 元，占目标成本 %s%%", remaining, remainingRate));
                    warnings.add(warning);
                }
            }
        }
        // 检查变更率
        List<CostChange> changes = changeMapper.selectList(new LambdaQueryWrapper<CostChange>()
                .eq(CostChange::getProjectId, projectId).eq(CostChange::getStatus, "CONFIRMED"));
        BigDecimal totalConfirmedChange = changes.stream()
                .map(c -> CostSupport.nvl(c.getConfirmedAmount()).subtract(CostSupport.nvl(c.getDeductAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CostContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<CostContract>()
                .eq(CostContract::getProjectId, projectId)
                .in(CostContract::getStatus, "EFFECTIVE", "SETTLED"));
        BigDecimal totalContractAmount = contracts.stream().map(c -> CostSupport.nvl(c.getSignedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalContractAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal changeRate = totalConfirmedChange.multiply(BigDecimal.valueOf(100))
                    .divide(totalContractAmount, 2, RoundingMode.HALF_UP);
            BigDecimal warnRate = new BigDecimal("5");
            if (changeRate.compareTo(warnRate) > 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("type", "CHANGE_RATE_HIGH");
                warning.put("level", "MEDIUM");
                warning.put("message", String.format("变更率 %s%% 超过预警阈值 %s%%", changeRate, warnRate));
                warnings.add(warning);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("warningCount", warnings.size());
        result.put("warnings", warnings);
        return result;
    }

    /**
     * 三算对比：目标成本 vs 动态成本 vs 实际发生成本
     * 目标成本：最新 APPROVED/LOCKED 版本
     * 动态成本：最新快照
     * 实际发生成本：已签合同 + 已确认变更 + 无效成本
     */
    public Map<String, Object> threeWayCompare(Long projectId) {
        if (projectId == null) throw new BizException("请指定项目");
        // 目标成本
        CostTargetVersion target = versionMapper.selectOne(new LambdaQueryWrapper<CostTargetVersion>()
                .eq(CostTargetVersion::getProjectId, projectId)
                .in(CostTargetVersion::getStatus, "APPROVED", "LOCKED")
                .orderByDesc(CostTargetVersion::getId).last("LIMIT 1"));
        BigDecimal targetTotal = target == null ? BigDecimal.ZERO : CostSupport.nvl(target.getTotalAmount());
        // 已签合同
        List<CostContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<CostContract>()
                .eq(CostContract::getProjectId, projectId)
                .in(CostContract::getStatus, "EFFECTIVE", "SETTLED"));
        BigDecimal contracted = contracts.stream().map(c -> CostSupport.nvl(c.getSignedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 变更
        List<CostChange> changes = changeMapper.selectList(new LambdaQueryWrapper<CostChange>()
                .eq(CostChange::getProjectId, projectId).eq(CostChange::getStatus, "CONFIRMED"));
        BigDecimal confirmedChange = changes.stream()
                .map(c -> CostSupport.nvl(c.getConfirmedAmount()).subtract(CostSupport.nvl(c.getDeductAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 无效成本
        List<CostInvalid> invalids = invalidMapper.selectList(new LambdaQueryWrapper<CostInvalid>()
                .eq(CostInvalid::getProjectId, projectId).eq(CostInvalid::getStatus, "CONFIRMED"));
        BigDecimal invalidTotal = invalids.stream().map(i -> CostSupport.nvl(i.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 实际发生成本 = 已签合同 + 确认变更 + 无效成本
        BigDecimal actualTotal = contracted.add(confirmedChange).add(invalidTotal);
        // 最新动态成本
        CostDynamicSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<CostDynamicSnapshot>()
                .eq(CostDynamicSnapshot::getProjectId, projectId)
                .orderByDesc(CostDynamicSnapshot::getSnapMonth).last("LIMIT 1"));
        BigDecimal dynamicTotal = latest == null ? BigDecimal.ZERO : CostSupport.nvl(latest.getDynamicTotal());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("targetTotal", targetTotal);
        result.put("dynamicTotal", dynamicTotal);
        result.put("actualTotal", actualTotal);
        result.put("targetVsDynamic", dynamicTotal.subtract(targetTotal));
        result.put("targetVsActual", actualTotal.subtract(targetTotal));
        result.put("dynamicVsActual", actualTotal.subtract(dynamicTotal));
        result.put("targetVsDynamicRate", targetTotal.compareTo(BigDecimal.ZERO) == 0 ? null
                : dynamicTotal.subtract(targetTotal).multiply(BigDecimal.valueOf(100))
                        .divide(targetTotal, 2, RoundingMode.HALF_UP));
        result.put("targetVsActualRate", targetTotal.compareTo(BigDecimal.ZERO) == 0 ? null
                : actualTotal.subtract(targetTotal).multiply(BigDecimal.valueOf(100))
                        .divide(targetTotal, 2, RoundingMode.HALF_UP));
        return result;
    }

    // ==================== 私有方法 ====================

    /** 估价中/估价审批中/确认中/确认审批中 均计入预估变更 */
    private boolean isInFlight(String status) {
        return "ESTIMATING".equals(status) || "ESTIMATE_APPROVING".equals(status)
                || "CONFIRMING".equals(status) || "APPROVING".equals(status);
    }

    /** 科目级构成：合同与合同变更按 alloc 分摊聚合，无合同变更直接挂科目 */
    private String buildDetailJson(List<CostContract> contracts, List<CostChange> changes) {
        Map<Long, BigDecimal> contractedByAccount = new LinkedHashMap<>();
        Map<Long, BigDecimal> changeByAccount = new LinkedHashMap<>();
        Map<Long, String> accountNames = new LinkedHashMap<>();
        for (CostContract c : contracts) {
            List<CostContractAlloc> allocs = allocsOf(c.getId());
            if (allocs.isEmpty()) {
                merge(contractedByAccount, UNALLOCATED, CostSupport.nvl(c.getSignedAmount()));
                accountNames.putIfAbsent(UNALLOCATED, "未分摊");
                continue;
            }
            for (CostContractAlloc a : allocs) {
                merge(contractedByAccount, a.getAccountId(), CostSupport.nvl(a.getAmount()));
                if (a.getAccountName() != null) accountNames.putIfAbsent(a.getAccountId(), a.getAccountName());
            }
        }
        for (CostChange ch : changes) {
            if (!"CONFIRMED".equals(ch.getStatus())) continue;
            BigDecimal net = CostSupport.nvl(ch.getConfirmedAmount()).subtract(CostSupport.nvl(ch.getDeductAmount()));
            if (ch.getContractId() == null) {
                // 无合同变更直接挂科目
                Long accountId = ch.getAccountId() == null ? UNALLOCATED : ch.getAccountId();
                merge(changeByAccount, accountId, net);
                if (ch.getAccountId() != null) {
                    CostMappers.AccountRef ref = mdmMapper.selectAccount(ch.getAccountId());
                    if (ref != null) accountNames.putIfAbsent(accountId, ref.getName());
                } else {
                    accountNames.putIfAbsent(UNALLOCATED, "未分摊");
                }
                continue;
            }
            // 合同变更按 alloc 分摊比例拆分到科目
            List<CostContractAlloc> allocs = allocsOf(ch.getContractId());
            BigDecimal totalAlloc = allocs.stream().map(a -> CostSupport.nvl(a.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocs.isEmpty() || totalAlloc.compareTo(BigDecimal.ZERO) == 0) {
                merge(changeByAccount, UNALLOCATED, net);
                accountNames.putIfAbsent(UNALLOCATED, "未分摊");
                continue;
            }
            for (CostContractAlloc a : allocs) {
                BigDecimal share = net.multiply(CostSupport.nvl(a.getAmount()))
                        .divide(totalAlloc, 2, RoundingMode.HALF_UP);
                merge(changeByAccount, a.getAccountId(), share);
                if (a.getAccountName() != null) accountNames.putIfAbsent(a.getAccountId(), a.getAccountName());
            }
        }
        Set<Long> accountIds = new LinkedHashSet<>();
        accountIds.addAll(contractedByAccount.keySet());
        accountIds.addAll(changeByAccount.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long accountId : accountIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", accountId < 0 ? null : accountId);
            row.put("accountName", accountNames.getOrDefault(accountId, ""));
            row.put("contracted", contractedByAccount.getOrDefault(accountId, BigDecimal.ZERO));
            row.put("change", changeByAccount.getOrDefault(accountId, BigDecimal.ZERO));
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<CostContractAlloc> allocsOf(Long contractId) {
        return allocMapper.selectList(new LambdaQueryWrapper<CostContractAlloc>()
                .eq(CostContractAlloc::getContractId, contractId));
    }

    private void merge(Map<Long, BigDecimal> map, Long key, BigDecimal amount) {
        map.merge(key, amount, BigDecimal::add);
    }
}
