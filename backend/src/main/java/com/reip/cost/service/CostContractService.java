package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostContractAlloc;
import com.reip.cost.entity.CostPaymentPlan;
import com.reip.cost.entity.CostPlanItem;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.framework.param.ParamService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同台账：评审 / 金额分摊科目 / 合约规划占用回写 / 结算 / 变更率预警 / 招采定标生成草稿
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostContractService {

    private final CostMappers.ContractMapper contractMapper;
    private final CostMappers.ContractAllocMapper allocMapper;
    private final CostMappers.PaymentPlanMapper paymentPlanMapper;
    private final CostMappers.PlanItemMapper planItemMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final EventPublisher eventPublisher;
    private final ParamService paramService;
    private final CostSupport support;
    private final CostChangeService changeService;

    // ==================== 查询 ====================

    public PageResult<CostContract> page(long current, long size, Long projectId, String status,
                                         Long supplierId, String kw) {
        LambdaQueryWrapper<CostContract> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostContract::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(CostContract::getStatus, status);
        if (supplierId != null) qw.eq(CostContract::getSupplierId, supplierId);
        if (kw != null && !kw.isBlank())
            qw.and(w -> w.like(CostContract::getName, kw).or().like(CostContract::getContractCode, kw));
        qw.orderByDesc(CostContract::getId);
        Page<CostContract> p = contractMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostContract detail(Long id) {
        return require(id);
    }

    // ==================== 台账 CRUD ====================

    @Transactional
    public Long create(CostContract contract) {
        if (contract == null) throw new BizException("缺少表单内容");
        if (contract.getProjectId() == null) throw new BizException("请选择项目");
        if (contract.getName() == null || contract.getName().isBlank()) throw new BizException("请填写合同名称");
        if (contract.getSignedAmount() == null) throw new BizException("请填写签约金额");
        if (contract.getPlanItemId() != null && planItemMapper.selectById(contract.getPlanItemId()) == null)
            throw new BizException("合约规划明细不存在");
        if (contract.getSupplierName() == null || contract.getSupplierName().isBlank())
            contract.setSupplierName(support.supplierName(contract.getSupplierId()));
        contract.setId(null);
        contract.setContractCode(codeRuleService.next("HT", support.codeCtx(contract.getProjectId())));
        contract.setStatus("DRAFT");
        if (contract.getSource() == null || contract.getSource().isBlank()) contract.setSource("MANUAL");
        contractMapper.insert(contract);
        return contract.getId();
    }

    @Transactional
    public void update(CostContract contract) {
        if (contract == null || contract.getId() == null) throw new BizException("缺少合同ID");
        CostContract db = require(contract.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的合同可修改");
        if (contract.getSupplierName() == null || contract.getSupplierName().isBlank())
            contract.setSupplierName(support.supplierName(contract.getSupplierId()));
        contract.setContractCode(null); // 编号不可修改
        contract.setStatus(db.getStatus()); // 状态不可通过修改接口变更
        if (contractMapper.updateById(contract) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostContract db = require(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的合同可删除");
        contractMapper.deleteById(id);
        allocMapper.delete(new LambdaQueryWrapper<CostContractAlloc>().eq(CostContractAlloc::getContractId, id));
        paymentPlanMapper.delete(new LambdaQueryWrapper<CostPaymentPlan>().eq(CostPaymentPlan::getContractId, id));
    }

    // ==================== 金额分摊科目 ====================

    public List<CostContractAlloc> allocs(Long contractId) {
        require(contractId);
        return allocMapper.selectList(new LambdaQueryWrapper<CostContractAlloc>()
                .eq(CostContractAlloc::getContractId, contractId));
    }

    @Transactional
    public Long addAlloc(Long contractId, CostContractAlloc alloc) {
        CostContract contract = requireDraft(contractId);
        if (alloc == null) throw new BizException("缺少分摊内容");
        if (alloc.getAmount() == null) throw new BizException("请填写分摊金额");
        alloc.setId(null);
        alloc.setContractId(contractId);
        alloc.setAccountName(support.account(alloc.getAccountId()).getName());
        allocMapper.insert(alloc);
        checkAllocSumNotExceed(contract);
        return alloc.getId();
    }

    @Transactional
    public void updateAlloc(CostContractAlloc alloc) {
        if (alloc == null || alloc.getId() == null) throw new BizException("缺少分摊ID");
        CostContractAlloc db = allocMapper.selectById(alloc.getId());
        if (db == null) throw new BizException("分摊记录不存在");
        CostContract contract = requireDraft(db.getContractId());
        if (alloc.getAmount() == null) throw new BizException("请填写分摊金额");
        alloc.setContractId(db.getContractId());
        alloc.setAccountName(support.account(alloc.getAccountId()).getName());
        if (allocMapper.updateById(alloc) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        checkAllocSumNotExceed(contract);
    }

    @Transactional
    public void deleteAlloc(Long contractId, Long allocId) {
        requireDraft(contractId);
        int deleted = allocMapper.delete(new LambdaQueryWrapper<CostContractAlloc>()
                .eq(CostContractAlloc::getId, allocId).eq(CostContractAlloc::getContractId, contractId));
        if (deleted == 0) throw new BizException("分摊记录不存在");
    }

    // ==================== 付款计划 ====================

    public List<CostPaymentPlan> paymentPlans(Long contractId) {
        require(contractId);
        return paymentPlanMapper.selectList(new LambdaQueryWrapper<CostPaymentPlan>()
                .eq(CostPaymentPlan::getContractId, contractId)
                .orderByAsc(CostPaymentPlan::getTermNo).orderByAsc(CostPaymentPlan::getId));
    }

    @Transactional
    public Long addPaymentPlan(Long contractId, CostPaymentPlan plan) {
        require(contractId);
        if (plan == null) throw new BizException("缺少付款计划内容");
        if (plan.getPlanAmount() == null || plan.getPlanAmount().signum() <= 0) throw new BizException("请填写计划金额");
        plan.setId(null);
        plan.setContractId(contractId);
        if (plan.getPaidAmount() == null) plan.setPaidAmount(BigDecimal.ZERO);
        paymentPlanMapper.insert(plan);
        return plan.getId();
    }

    @Transactional
    public void updatePaymentPlan(CostPaymentPlan plan) {
        if (plan == null || plan.getId() == null) throw new BizException("缺少付款计划ID");
        CostPaymentPlan db = paymentPlanMapper.selectById(plan.getId());
        if (db == null) throw new BizException("付款计划不存在");
        plan.setContractId(db.getContractId());
        if (paymentPlanMapper.updateById(plan) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deletePaymentPlan(Long contractId, Long planId) {
        CostPaymentPlan db = paymentPlanMapper.selectOne(new LambdaQueryWrapper<CostPaymentPlan>()
                .eq(CostPaymentPlan::getId, planId).eq(CostPaymentPlan::getContractId, contractId));
        if (db == null) throw new BizException("付款计划不存在");
        if (CostSupport.nvl(db.getPaidAmount()).signum() > 0)
            throw new BizException("该期已发生付款 " + CostSupport.nvl(db.getPaidAmount()) + " 元，不能删除付款计划");
        if (paymentPlanMapper.deleteById(planId) == 0) throw new BizException("付款计划不存在");
    }

    // ==================== 评审 / 工作流回调 ====================

    /** 提交评审（bizType=COST_CONTRACT，金额=signed_amount） */
    @Transactional
    public void submit(Long id) {
        CostContract c = require(id);
        if (!"DRAFT".equals(c.getStatus())) throw new BizException("仅草稿状态的合同可提交评审");
        BigDecimal allocSum = allocSum(id);
        if (allocSum.compareTo(CostSupport.nvl(c.getSignedAmount())) != 0)
            throw new BizException("分摊金额合计与签约金额不一致，请先完善科目分摊");
        // 合约规划余量管控：校验规划明细余额是否充足
        if (c.getPlanItemId() != null) {
            checkPlanRemaining(c.getPlanItemId(), CostSupport.nvl(c.getSignedAmount()));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractCode", c.getContractCode());
        snapshot.put("name", c.getName());
        snapshot.put("supplierName", c.getSupplierName());
        snapshot.put("signedAmount", c.getSignedAmount());
        snapshot.put("projectId", c.getProjectId());
        workflowClient.start("COST_CONTRACT", id, "合同评审 " + c.getName(), c.getSignedAmount(), snapshot);
        CostContract up = new CostContract();
        up.setId(id);
        up.setVersion(c.getVersion());
        up.setStatus("APPROVING");
        if (contractMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        // 提交即在合约规划明细上登记在途占用
        if (c.getPlanItemId() != null)
            planItemMapper.addPending(c.getPlanItemId(), CostSupport.nvl(c.getSignedAmount()));
    }

    /** 工作流回调：通过 → 生效；原子回写规划占用（在途转已签约）；发布签约事件 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostContract c = contractMapper.selectById(id);
        if (c == null || !"APPROVING".equals(c.getStatus())) return; // 幂等：非在途状态忽略
        CostContract up = new CostContract();
        up.setId(id);
        up.setVersion(c.getVersion());
        up.setStatus("EFFECTIVE");
        contractMapper.updateById(up);
        BigDecimal amount = CostSupport.nvl(c.getSignedAmount());
        if (c.getPlanItemId() != null) {
            // 原子回写：在途占用转已签约占用（行锁安全，并发不超占）
            planItemMapper.addPending(c.getPlanItemId(), amount.negate());
            planItemMapper.addContracted(c.getPlanItemId(), amount);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", c.getProjectId());
        payload.put("contractId", c.getId());
        payload.put("planItemId", c.getPlanItemId());
        payload.put("supplierId", c.getSupplierId());
        payload.put("supplierName", c.getSupplierName());
        payload.put("amount", amount);
        eventPublisher.publish("cost.contract.signed", "COST_CONTRACT", c.getId(), payload);
    }

    /** 工作流回调：驳回 → 回 DRAFT；回滚在途占用 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostContract c = contractMapper.selectById(id);
        if (c == null || !"APPROVING".equals(c.getStatus())) return;
        CostContract up = new CostContract();
        up.setId(id);
        up.setVersion(c.getVersion());
        up.setStatus("DRAFT");
        contractMapper.updateById(up);
        if (c.getPlanItemId() != null)
            planItemMapper.addPending(c.getPlanItemId(), CostSupport.nvl(c.getSignedAmount()).negate());
    }

    // ==================== 结算 / 变更率 ====================

    /** 结算：结算金额=签约+确认变更-扣款（强校验） */
    @Transactional
    public void settle(Long id, BigDecimal settledAmount) {
        CostContract c = require(id);
        if (!"EFFECTIVE".equals(c.getStatus())) throw new BizException("仅生效状态的合同可结算");
        if (settledAmount == null) throw new BizException("请填写结算金额");
        BigDecimal expected = CostSupport.nvl(c.getSignedAmount()).add(changeService.confirmedNetByContract(id));
        if (settledAmount.compareTo(expected) != 0)
            throw new BizException("结算金额与\"签约金额+确认变更-扣款\"不一致，应为 " + expected + " 元");
        CostContract up = new CostContract();
        up.setId(id);
        up.setVersion(c.getVersion());
        up.setStatus("SETTLED");
        up.setSettledAmount(settledAmount);
        if (contractMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 质保金释放：质保期满后释放质保金 */
    @Transactional
    public void releaseRetention(Long id, BigDecimal releaseAmount) {
        CostContract c = require(id);
        if (!"SETTLED".equals(c.getStatus()) && !"EFFECTIVE".equals(c.getStatus()))
            throw new BizException("仅已结算或已生效的合同可释放质保金");
        if (releaseAmount == null || releaseAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BizException("请填写释放金额");
        BigDecimal retentionAmount = CostSupport.nvl(c.getSignedAmount())
                .multiply(CostSupport.nvl(c.getRetentionRate()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal released = CostSupport.nvl(c.getRetentionReleased());
        if (releaseAmount.add(released).compareTo(retentionAmount) > 0)
            throw new BizException("释放金额超过质保金总额：" + retentionAmount + " 元，已释放 " + released + " 元");
        CostContract up = new CostContract();
        up.setId(id);
        up.setVersion(c.getVersion());
        up.setRetentionReleased(released.add(releaseAmount));
        if (contractMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 变更率预警：确认变更累计/签约金额，超 cost.change.warn_rate（默认5%）返回 warn=true */
    public Map<String, Object> changeRate(Long contractId) {
        CostContract c = require(contractId);
        BigDecimal signed = CostSupport.nvl(c.getSignedAmount());
        BigDecimal confirmed = changeService.confirmedNetByContract(contractId);
        BigDecimal rate = signed.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : confirmed.multiply(BigDecimal.valueOf(100)).divide(signed, 2, RoundingMode.HALF_UP);
        BigDecimal warnRate = paramService.getDecimal("cost.change.warn_rate", new BigDecimal("5"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", contractId);
        result.put("signedAmount", signed);
        result.put("confirmedChange", confirmed);
        result.put("changeRate", rate);
        result.put("warnRate", warnRate);
        result.put("warn", rate.compareTo(warnRate) > 0);
        return result;
    }

    // ==================== 招采定标生成草稿 ====================

    /** 监听 procurement.award.confirmed 自动创建合同草稿（幂等：同定标单仅生成一次） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createFromAward(Long projectId, Long awardId, Long supplierId, String supplierName,
                                BigDecimal amount, Long planItemId, String title) {
        CostContract exists = contractMapper.selectOne(new LambdaQueryWrapper<CostContract>()
                .eq(CostContract::getSource, "AWARD").eq(CostContract::getAwardId, awardId)
                .last("LIMIT 1"));
        if (exists != null) return exists.getId();
        CostContract c = new CostContract();
        c.setProjectId(projectId);
        c.setName(title);
        c.setSupplierId(supplierId);
        c.setSupplierName(supplierName == null || supplierName.isBlank()
                ? support.supplierName(supplierId) : supplierName);
        c.setSignedAmount(amount);
        c.setPlanItemId(planItemId);
        c.setSource("AWARD");
        c.setAwardId(awardId);
        c.setContractCode(codeRuleService.next("HT", support.codeCtx(projectId)));
        c.setStatus("DRAFT");
        contractMapper.insert(c);
        return c.getId();
    }

    // ==================== 私有方法 ====================

    private CostContract require(Long id) {
        if (id == null) throw new BizException("缺少合同ID");
        CostContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException("合同不存在");
        return c;
    }

    private CostContract requireDraft(Long contractId) {
        CostContract c = require(contractId);
        if (!"DRAFT".equals(c.getStatus())) throw new BizException("仅草稿状态的合同可维护分摊科目");
        return c;
    }

    private BigDecimal allocSum(Long contractId) {
        return allocMapper.selectList(new LambdaQueryWrapper<CostContractAlloc>()
                        .eq(CostContractAlloc::getContractId, contractId)).stream()
                .map(a -> CostSupport.nvl(a.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 分摊维护校验：合计不得超过签约金额（提交时再强校验相等） */
    private void checkAllocSumNotExceed(CostContract contract) {
        BigDecimal sum = allocSum(contract.getId());
        if (sum.compareTo(CostSupport.nvl(contract.getSignedAmount())) > 0)
            throw new BizException("分摊金额合计超过签约金额：" + sum);
    }

    /**
     * 合约规划余量管控：校验规划明细余额是否充足
     * 余额 = 规划金额 - 已签约占用 - 在途占用
     * 控制模式：阻断（默认）或预警（可通过参数配置）
     */
    private void checkPlanRemaining(Long planItemId, BigDecimal amount) {
        CostPlanItem planItem = planItemMapper.selectById(planItemId);
        if (planItem == null) return;
        BigDecimal planned = CostSupport.nvl(planItem.getPlannedAmount());
        BigDecimal contracted = CostSupport.nvl(planItem.getContractedAmount());
        BigDecimal pending = CostSupport.nvl(planItem.getPendingAmount());
        BigDecimal remaining = planned.subtract(contracted).subtract(pending);
        if (amount.compareTo(remaining) > 0) {
            boolean blockMode = paramService.getBool("cost.plan.remaining_block", true);
            String msg = String.format("合约规划余额不足：规划金额 %s 元，已签约 %s 元，在途 %s 元，余额 %s 元，本次申请 %s 元",
                    planned, contracted, pending, remaining, amount);
            if (blockMode) {
                throw new BizException(msg + "，已阻断提交");
            } else {
                // 预警模式：记录日志但不阻断
                log.warn("[cost] {}", msg);
            }
        }
    }
}
