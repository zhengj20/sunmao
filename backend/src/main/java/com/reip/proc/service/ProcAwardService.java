package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.param.ParamService;
import com.reip.proc.entity.ProcAward;
import com.reip.proc.entity.ProcBid;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.mapper.ProcMappers.AwardMapper;
import com.reip.proc.mapper.ProcMappers.BidMapper;
import com.reip.proc.mapper.ProcMappers.CostPlanItemMapper;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定标（核心）：定标控制校验（合约规划余量）+ 审批 + 事件驱动联动成本
 */
@Service
@RequiredArgsConstructor
public class ProcAwardService {

    private final AwardMapper awardMapper;
    private final ProjectMapper projectMapper;
    private final BidMapper bidMapper;
    private final CostPlanItemMapper costPlanItemMapper;
    private final CodeRuleService codeRuleService;
    private final ParamService paramService;
    private final WorkflowClient workflowClient;

    public PageResult<ProcAward> page(long current, long size, Long procProjectId, String status) {
        LambdaQueryWrapper<ProcAward> qw = new LambdaQueryWrapper<>();
        if (procProjectId != null) qw.eq(ProcAward::getProcProjectId, procProjectId);
        if (status != null && !status.isBlank()) qw.eq(ProcAward::getStatus, status);
        qw.orderByDesc(ProcAward::getId);
        Page<ProcAward> p = awardMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcAward detail(Long id) {
        ProcAward a = awardMapper.selectById(id);
        if (a == null) throw new BizException("定标记录不存在");
        return a;
    }

    /**
     * 创建定标：
     * 1) 中标人必须在回标名单中；
     * 2) 定标控制：余量=合约规划 planned−contracted−pending，超出按 cost.plan.over_policy（BLOCK 阻断/WARN 预警）。
     */
    @Transactional
    public Long create(ProcAward award) {
        if (award.getProcProjectId() == null) throw new BizException("缺少招采立项ID");
        if (award.getWinnerSupplierId() == null) throw new BizException("缺少中标供应商");
        if (award.getAwardAmount() == null) throw new BizException("缺少定标价");
        ProcProject project = projectMapper.selectById(award.getProcProjectId());
        if (project == null) throw new BizException("招采立项不存在");
        Long cnt = awardMapper.selectCount(new LambdaQueryWrapper<ProcAward>()
                .eq(ProcAward::getProcProjectId, project.getId()));
        if (cnt != null && cnt > 0) throw new BizException("该立项已存在定标记录");
        // 中标人必须在回标名单中
        List<ProcBid> bids = bidMapper.selectList(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getProcProjectId, project.getId()));
        ProcBid winnerBid = bids.stream()
                .filter(b -> award.getWinnerSupplierId().equals(b.getSupplierId()))
                .findFirst().orElse(null);
        if (winnerBid == null) throw new BizException("中标人不在回标名单中");
        award.setWinnerSupplierName(winnerBid.getSupplierName());
        award.setBudgetAmount(project.getBudgetAmount());
        if (project.getBudgetAmount() != null) {
            award.setDeviation(award.getAwardAmount().subtract(project.getBudgetAmount()));
            if (project.getBudgetAmount().compareTo(BigDecimal.ZERO) > 0) {
                award.setDeviationPct(award.getDeviation().divide(project.getBudgetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            }
        }
        award.setControlCheck(checkControl(project, award.getAwardAmount()));
        award.setAwardCode(codeRuleService.next("DB", null));
        award.setNoticeStatus("UNSENT");
        award.setContractGenerated(0);
        award.setStatus("DRAFT");
        awardMapper.insert(award);
        return award.getId();
    }

    /** 提交审批（PROC_AWARD，金额=定标价，摘要含中标人/定标价/预算/余量） */
    @Transactional
    public void submit(Long id) {
        ProcAward award = detail(id);
        if (!"DRAFT".equals(award.getStatus())) throw new BizException("仅草稿状态的定标可提交审批");
        ProcProject project = projectMapper.selectById(award.getProcProjectId());
        if (project == null) throw new BizException("招采立项不存在");
        BigDecimal remaining = null;
        if (project.getPlanItemId() != null) {
            Map<String, Object> item = costPlanItemMapper.selectPlanItem(project.getPlanItemId());
            if (item != null) remaining = remainingOf(item);
        }
        award.setStatus("APPROVING");
        if (awardMapper.updateById(award) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("定标编号", award.getAwardCode());
        snapshot.put("标的", project.getTitle());
        snapshot.put("中标人", award.getWinnerSupplierName());
        snapshot.put("定标价", award.getAwardAmount());
        snapshot.put("预算", award.getBudgetAmount());
        if (remaining != null) snapshot.put("合约规划余量", remaining);
        snapshot.put("定标控制", award.getControlCheck());
        workflowClient.start("PROC_AWARD", id,
                "定标审批：" + project.getTitle() + "-" + award.getWinnerSupplierName(),
                award.getAwardAmount(), snapshot);
    }

    /** 定标控制校验：余量内 PASSED；超出按超余量策略（BLOCK 阻断 / WARN 预警） */
    private String checkControl(ProcProject project, BigDecimal awardAmount) {
        if (project.getPlanItemId() == null) return null;
        Map<String, Object> item = costPlanItemMapper.selectPlanItem(project.getPlanItemId());
        if (item == null) return null;
        BigDecimal remaining = remainingOf(item);
        if (awardAmount.compareTo(remaining) <= 0) return "PASSED";
        String policy = paramService.get("cost.plan.over_policy", "WARN");
        if ("BLOCK".equalsIgnoreCase(policy)) throw new BizException("定标价超合约规划余量，已阻断");
        return "WARN";
    }

    /** 余量 = 规划金额 − 已签约占用 − 在途占用 */
    private BigDecimal remainingOf(Map<String, Object> item) {
        BigDecimal planned = toDecimal(item.get("plannedAmount"));
        BigDecimal contracted = toDecimal(item.get("contractedAmount"));
        BigDecimal pending = toDecimal(item.get("pendingAmount"));
        return planned.subtract(contracted).subtract(pending);
    }

    private BigDecimal toDecimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }
}
