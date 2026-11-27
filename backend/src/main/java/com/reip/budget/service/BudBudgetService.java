package com.reip.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.budget.entity.BudAdjust;
import com.reip.budget.entity.BudBudget;
import com.reip.budget.entity.BudBudgetMonth;
import com.reip.budget.mapper.BudMappers;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.mdm.entity.MdmCostAccount;
import com.reip.mdm.entity.MdmExpenseAccount;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 全面预算：年度预算（编制/生效/调整）+ 月度分解（分解/调整/跨月调剂）
 * 规则：B1 年度控制月度（Σ月度 ≤ 年度，硬控）；B2 年度下调 ≥ 已分解/已执行
 */
@Service
@RequiredArgsConstructor
public class BudBudgetService {

    private final BudMappers.BudgetMapper budgetMapper;
    private final BudMappers.MonthMapper monthMapper;
    private final BudMappers.AdjustMapper adjustMapper;
    private final BudMappers.CostExecMapper costExecMapper;
    private final BudMappers.ExpenseExecMapper expenseExecMapper;
    private final MdmMappers.CostAccountMapper accountMapper;
    private final MdmMappers.ExpenseAccountMapper expenseAccountMapper;
    private final MdmMappers.OrgMapper orgMapper;
    private final MdmMappers.ProjectMapper projectMapper;
    private final CodeRuleService codeRuleService;
    private final BudExecService execService;

    // ==================== 查询 ====================

    public PageResult<BudBudget> page(long current, long size, Integer year, String type,
                                      String status, Long projectId, Long deptId, String kw) {
        LambdaQueryWrapper<BudBudget> qw = new LambdaQueryWrapper<>();
        if (year != null) qw.eq(BudBudget::getBudgetYear, year);
        if (type != null && !type.isBlank()) qw.eq(BudBudget::getBudgetType, type);
        if (status != null && !status.isBlank()) qw.eq(BudBudget::getStatus, status);
        if (projectId != null) qw.eq(BudBudget::getProjectId, projectId);
        if (deptId != null) qw.eq(BudBudget::getDeptId, deptId);
        if (kw != null && !kw.isBlank()) qw.like(BudBudget::getAccountName, kw);
        qw.orderByDesc(BudBudget::getBudgetYear).orderByAsc(BudBudget::getBudgetType).orderByDesc(BudBudget::getId);
        Page<BudBudget> p = budgetMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fillNames);
        execService.fillAnnualActuals(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 年度预算详情（含月度分解 + 最近调整留痕） */
    public Map<String, Object> detail(Long id) {
        BudBudget b = require(id);
        fillNames(b);
        execService.fillAnnualActuals(List.of(b));
        b.setMonths(months(id));
        List<BudAdjust> recent = adjustMapper.selectList(new LambdaQueryWrapper<BudAdjust>()
                .eq(BudAdjust::getBudgetId, id).orderByDesc(BudAdjust::getId));
        return Map.of("budget", b, "recentAdjusts", recent.size() > 10 ? recent.subList(0, 10) : recent);
    }

    /** 月度分解（完整 12 个月：已分解行 + 未分解空行；含执行/余额/超预算标记） */
    public List<BudBudgetMonth> months(Long budgetId) {
        BudBudget b = require(budgetId);
        Map<Integer, BudBudgetMonth> byMonth = monthMapper.selectList(new LambdaQueryWrapper<BudBudgetMonth>()
                        .eq(BudBudgetMonth::getBudgetId, budgetId)).stream()
                .collect(Collectors.toMap(BudBudgetMonth::getMonth, m -> m, (x, y) -> x));
        List<BudBudgetMonth> list = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            BudBudgetMonth row = byMonth.get(m);
            if (row == null) { row = new BudBudgetMonth(); row.setMonth(m); }
            list.add(row);
        }
        execService.fillMonths(b, list);
        return list;
    }

    /** 调整留痕分页 */
    public PageResult<BudAdjust> adjustPage(long current, long size, Long budgetId) {
        LambdaQueryWrapper<BudAdjust> qw = new LambdaQueryWrapper<>();
        if (budgetId != null) qw.eq(BudAdjust::getBudgetId, budgetId);
        qw.orderByDesc(BudAdjust::getId);
        Page<BudAdjust> p = adjustMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    // ==================== 年度预算 ====================

    @Transactional
    public Long create(BudBudget b) {
        if (b == null) throw new BizException("缺少预算内容");
        if (b.getBudgetYear() == null) throw new BizException("请填写预算年度");
        if (!"COST".equals(b.getBudgetType()) && !"EXPENSE".equals(b.getBudgetType()))
            throw new BizException("预算类型须为 COST(成本线) 或 EXPENSE(费用线)");
        if ("COST".equals(b.getBudgetType())) {
            if (b.getProjectId() == null) throw new BizException("成本预算请选择项目");
            b.setDeptId(null);
        } else {
            if (b.getDeptId() == null) throw new BizException("费用预算请选择部门");
            b.setProjectId(null);
        }
        if (b.getAccountId() == null) throw new BizException("请选择预算科目");
        // 成本线取成本科目、费用线取费用科目（两棵独立科目树）
        String ctrlMode;
        if ("COST".equals(b.getBudgetType())) {
            MdmCostAccount acc = accountMapper.selectById(b.getAccountId());
            if (acc == null) throw new BizException("成本科目不存在：" + b.getAccountId());
            ctrlMode = acc.getControlMode();
        } else {
            MdmExpenseAccount acc = expenseAccountMapper.selectById(b.getAccountId());
            if (acc == null) throw new BizException("费用科目不存在：" + b.getAccountId());
            ctrlMode = acc.getControlMode();
        }
        BudSupport.requireAmount(b.getAnnualAmount(), "年度预算额");
        checkDimensionUnique(b.getBudgetYear(), b.getBudgetType(), b.getProjectId(), b.getDeptId(), b.getAccountId(), null);
        b.setId(null);
        b.setBudgetCode(codeRuleService.next("QYYS", Map.of()));
        if (b.getControlMode() == null || b.getControlMode().isBlank()) b.setControlMode(ctrlMode);
        b.setAllocatedAmount(BigDecimal.ZERO);
        if (b.getStatus() == null || b.getStatus().isBlank()) b.setStatus("DRAFT");
        budgetMapper.insert(b);
        return b.getId();
    }

    /** 生效：DRAFT → APPROVED（生效后参与管控） */
    @Transactional
    public void activate(Long id) {
        BudBudget b = require(id);
        if (!"DRAFT".equals(b.getStatus())) throw new BizException("仅草稿状态可生效");
        updateStatus(b, "APPROVED");
    }

    /** 归档：APPROVED → ARCHIVED（退出管控） */
    @Transactional
    public void archive(Long id) {
        BudBudget b = require(id);
        if (!"APPROVED".equals(b.getStatus())) throw new BizException("仅生效状态可归档");
        updateStatus(b, "ARCHIVED");
    }

    @Transactional
    public void delete(Long id) {
        BudBudget b = require(id);
        long months = monthMapper.selectCount(new LambdaQueryWrapper<BudBudgetMonth>()
                .eq(BudBudgetMonth::getBudgetId, id));
        if (months > 0) throw new BizException("该预算已有月度分解，不能删除");
        BigDecimal actual = execService.annualActual(b);
        if (actual.signum() > 0) throw new BizException("该预算已发生执行，不能删除");
        budgetMapper.deleteById(id);
    }

    /**
     * 年度调整（B2）：调增直接放行；调减后年度额须 ≥ 已分解 且 ≥ 已执行
     */
    @Transactional
    public Long adjustAnnual(Long id, String adjustType, BigDecimal amount, String reason) {
        BudBudget b = require(id);
        if ("ARCHIVED".equals(b.getStatus())) throw new BizException("已归档预算不能调整");
        if (!"INCREASE".equals(adjustType) && !"DECREASE".equals(adjustType))
            throw new BizException("调整方向须为 INCREASE/DECREASE");
        BudSupport.requireAmount(amount, "调整金额");
        if (reason == null || reason.isBlank()) throw new BizException("请填写调整原因");
        BigDecimal delta = "INCREASE".equals(adjustType) ? amount : amount.negate();
        BigDecimal newAnnual = BudSupport.nvl(b.getAnnualAmount()).add(delta);
        if (newAnnual.signum() < 0) throw new BizException("调整额超过年度预算，不能调减为负");
        if (delta.signum() < 0) {
            BigDecimal allocated = monthSum(id);
            if (newAnnual.compareTo(allocated) < 0)
                throw new BizException("年度调减后低于已分解月度预算 " + allocated + "，请先调减月度分解");
            BigDecimal actual = execService.annualActual(b);
            if (newAnnual.compareTo(actual) < 0)
                throw new BizException("年度调减后低于已执行额 " + actual + "，不能下调");
        }
        BudBudget up = new BudBudget();
        up.setId(id);
        up.setVersion(b.getVersion());
        up.setAnnualAmount(newAnnual);
        if (budgetMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return saveAdjust(b, null, adjustType, amount, reason);
    }

    // ==================== 月度分解 ====================

    /** 新增/更新某月预算（B1：Σ月度 ≤ 年度） */
    @Transactional
    public Long upsertMonth(Long budgetId, Integer month, BigDecimal amount) {
        return doUpsertMonth(budgetId, month, amount, true);
    }

    private Long doUpsertMonth(Long budgetId, Integer month, BigDecimal amount, boolean withB1) {
        BudBudget b = require(budgetId);
        if ("ARCHIVED".equals(b.getStatus())) throw new BizException("已归档预算已退出管控，不能维护月度预算");
        if (month == null || month < 1 || month > 12) throw new BizException("月份须为 1-12");
        BudSupport.requireAmount(amount, "月度预算额");
        BudBudgetMonth exists = monthMapper.selectOne(new LambdaQueryWrapper<BudBudgetMonth>()
                .eq(BudBudgetMonth::getBudgetId, budgetId).eq(BudBudgetMonth::getMonth, month));
        if (withB1) checkB1(b, budgetId, month, amount, exists == null ? null : exists.getId());
        BigDecimal actual = execService.monthActual(b, month);
        if (amount.compareTo(actual) < 0)
            throw new BizException(month + "月预算不能低于已执行额 " + actual);
        if (exists == null) {
            BudBudgetMonth m = new BudBudgetMonth();
            m.setBudgetId(budgetId);
            m.setMonth(month);
            m.setMonthAmount(amount);
            m.setStatus("ACTIVE");
            monthMapper.insert(m);
            refreshAllocated(b);
            return m.getId();
        }
        BudBudgetMonth up = new BudBudgetMonth();
        up.setId(exists.getId());
        up.setVersion(exists.getVersion());
        up.setMonthAmount(amount);
        if (monthMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        refreshAllocated(b);
        return exists.getId();
    }

    /** 月度调整（B1 + 不低于已执行） */
    @Transactional
    public Long adjustMonth(Long monthId, String adjustType, BigDecimal amount, String reason) {
        BudBudgetMonth m = monthMapper.selectById(monthId);
        if (m == null) throw new BizException("月度预算不存在");
        if (!"INCREASE".equals(adjustType) && !"DECREASE".equals(adjustType))
            throw new BizException("调整方向须为 INCREASE/DECREASE");
        BudSupport.requireAmount(amount, "调整金额");
        if (reason == null || reason.isBlank()) throw new BizException("请填写调整原因");
        BudBudget b = require(m.getBudgetId());
        if ("ARCHIVED".equals(b.getStatus())) throw new BizException("已归档预算已退出管控，不能调整月度预算");
        BigDecimal delta = "INCREASE".equals(adjustType) ? amount : amount.negate();
        BigDecimal newAmount = BudSupport.nvl(m.getMonthAmount()).add(delta);
        if (newAmount.signum() < 0) throw new BizException("月度预算不能调减为负");
        checkB1(b, m.getBudgetId(), m.getMonth(), newAmount, m.getId());
        BigDecimal actual = execService.monthActual(b, m.getMonth());
        if (newAmount.compareTo(actual) < 0)
            throw new BizException(m.getMonth() + "月预算不能低于已执行额 " + actual);
        BudBudgetMonth up = new BudBudgetMonth();
        up.setId(m.getId());
        up.setVersion(m.getVersion());
        up.setMonthAmount(newAmount);
        if (monthMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        refreshAllocated(b);
        return saveAdjust(b, m.getId(), adjustType, amount, reason);
    }

    /** 跨月调剂（源月调减 + 目标月调增，B1） */
    @Transactional
    public void reallocate(Long budgetId, Integer fromMonth, Integer toMonth, BigDecimal amount, String reason) {
        if (fromMonth == null || toMonth == null || fromMonth.equals(toMonth))
            throw new BizException("请选择不同的源月与目标月");
        if (fromMonth < 1 || fromMonth > 12 || toMonth < 1 || toMonth > 12)
            throw new BizException("月份须为 1-12");
        if (reason == null || reason.isBlank()) throw new BizException("请填写调剂原因");
        BudBudget b = require(budgetId);
        if ("ARCHIVED".equals(b.getStatus())) throw new BizException("已归档预算已退出管控，不能跨月调剂");
        BudBudgetMonth src = monthMapper.selectOne(new LambdaQueryWrapper<BudBudgetMonth>()
                .eq(BudBudgetMonth::getBudgetId, budgetId).eq(BudBudgetMonth::getMonth, fromMonth));
        if (src == null) throw new BizException(fromMonth + "月尚无月度预算，无法调出");
        BudSupport.requireAmount(amount, "调剂金额");
        BigDecimal actual = execService.monthActual(b, fromMonth);
        if (BudSupport.nvl(src.getMonthAmount()).subtract(amount).compareTo(actual) < 0)
            throw new BizException(fromMonth + "月已执行 " + actual + "，可调出额度不足");
        // 目标月调增须满足 B1
        BigDecimal allocated = monthSum(budgetId);
        if (allocated.compareTo(b.getAnnualAmount()) > 0)
            throw new BizException("目标月已超年度预算（B1），请先调减其他月份");
        // 源月调减
        BudBudgetMonth up = new BudBudgetMonth();
        up.setId(src.getId());
        up.setVersion(src.getVersion());
        up.setMonthAmount(src.getMonthAmount().subtract(amount));
        if (monthMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        // 目标月调增
        BudBudgetMonth dst = monthMapper.selectOne(new LambdaQueryWrapper<BudBudgetMonth>()
                .eq(BudBudgetMonth::getBudgetId, budgetId).eq(BudBudgetMonth::getMonth, toMonth));
        if (dst == null) {
            BudBudgetMonth m = new BudBudgetMonth();
            m.setBudgetId(budgetId);
            m.setMonth(toMonth);
            m.setMonthAmount(amount);
            m.setStatus("ACTIVE");
            monthMapper.insert(m);
        } else {
            BudBudgetMonth du = new BudBudgetMonth();
            du.setId(dst.getId());
            du.setVersion(dst.getVersion());
            du.setMonthAmount(dst.getMonthAmount().add(amount));
            if (monthMapper.updateById(du) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }
        refreshAllocated(b);
        saveAdjust(b, src.getId(), "DECREASE", amount, reason + "（" + fromMonth + "月调出）");
        saveAdjust(b, null, "INCREASE", amount, reason + "（" + toMonth + "月调入）");
    }

    /**
     * 一键分解（整体重建）：逐月覆盖既有月度行（已有月更新、缺失月新增），末月取余数
     * 保证 Σ月度 = 年度，B1 结构性成立；EVEN 平均 12 月 / RATIO 自定义比例（和 ≤ 100%）
     * 注意：不能用逻辑删除+新增（逻辑删除行仍占用唯一键 uk_bm）
     */
    @Transactional
    public int generateMonths(Long budgetId, String mode, List<BigDecimal> ratios) {
        BudBudget b = require(budgetId);
        if ("ARCHIVED".equals(b.getStatus())) throw new BizException("已归档预算已退出管控，不能分解月度预算");
        BigDecimal annual = BudSupport.nvl(b.getAnnualAmount());
        List<BigDecimal> parts = new ArrayList<>(12);
        BigDecimal used = BigDecimal.ZERO;
        if ("RATIO".equals(mode)) {
            if (ratios == null || ratios.isEmpty()) throw new BizException("请提供 12 个月比例");
            BigDecimal sum = ratios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.ONE) > 0) throw new BizException("比例合计超过 100%（B1）");
            for (int i = 0; i < 12; i++) {
                BigDecimal p = (i == 11) ? annual.subtract(used)
                        : annual.multiply(ratios.get(i)).setScale(2, RoundingMode.HALF_UP);
                parts.add(p);
                used = used.add(p);
            }
        } else {
            BigDecimal base = annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            for (int i = 0; i < 12; i++) {
                BigDecimal p = (i == 11) ? annual.subtract(used) : base;
                parts.add(p);
                used = used.add(p);
            }
        }
        int n = 0;
        for (int i = 0; i < 12; i++) {
            if (parts.get(i) == null || parts.get(i).signum() <= 0) continue;
            doUpsertMonth(budgetId, i + 1, parts.get(i), false);
            n++;
        }
        refreshAllocated(b);
        return n;
    }

    // ==================== 私有 ====================

    /** B1：Σ月度（排除被更新行）+ 新月度 ≤ 年度 */
    private void checkB1(BudBudget b, Long budgetId, Integer month, BigDecimal newAmount, Long excludeId) {
        List<BudBudgetMonth> list = monthMapper.selectList(new LambdaQueryWrapper<BudBudgetMonth>()
                .eq(BudBudgetMonth::getBudgetId, budgetId));
        BigDecimal others = list.stream()
                .filter(m -> excludeId == null || !excludeId.equals(m.getId()))
                .map(BudBudgetMonth::getMonthAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = others.add(newAmount);
        if (total.compareTo(BudSupport.nvl(b.getAnnualAmount())) > 0)
            throw new BizException("月度预算合计 " + total + " 超过年度预算 " + b.getAnnualAmount() + "（B1 年度控制月度）");
    }

    private BigDecimal monthSum(Long budgetId) {
        return monthMapper.selectList(new LambdaQueryWrapper<BudBudgetMonth>()
                        .eq(BudBudgetMonth::getBudgetId, budgetId)).stream()
                .map(BudBudgetMonth::getMonthAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void refreshAllocated(BudBudget b) {
        BudBudget up = new BudBudget();
        up.setId(b.getId());
        up.setVersion(b.getVersion());
        up.setAllocatedAmount(monthSum(b.getId()));
        budgetMapper.updateById(up);
    }

    private void updateStatus(BudBudget b, String status) {
        BudBudget up = new BudBudget();
        up.setId(b.getId());
        up.setVersion(b.getVersion());
        up.setStatus(status);
        if (budgetMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    private Long saveAdjust(BudBudget b, Long monthId, String adjustType, BigDecimal amount, String reason) {
        BudAdjust adj = new BudAdjust();
        adj.setAdjustCode(codeRuleService.next("TZ", Map.of()));
        adj.setBudgetId(b.getId());
        adj.setMonthId(monthId);
        adj.setAdjustType(adjustType);
        adj.setAmount(amount);
        adj.setReason(reason);
        adj.setStatus("APPROVED");
        adjustMapper.insert(adj);
        return adj.getId();
    }

    private void checkDimensionUnique(Integer year, String type, Long projectId, Long deptId,
                                      Long accountId, Long excludeId) {
        LambdaQueryWrapper<BudBudget> qw = new LambdaQueryWrapper<BudBudget>()
                .eq(BudBudget::getBudgetYear, year)
                .eq(BudBudget::getBudgetType, type)
                .eq(BudBudget::getAccountId, accountId)
                .ne(excludeId != null, BudBudget::getId, excludeId);
        if (projectId != null) qw.eq(BudBudget::getProjectId, projectId);
        if (deptId != null) qw.eq(BudBudget::getDeptId, deptId);
        if (projectId == null && deptId == null) qw.isNull(BudBudget::getProjectId).isNull(BudBudget::getDeptId);
        if (projectId != null) qw.isNull(BudBudget::getDeptId);
        if (deptId != null) qw.isNull(BudBudget::getProjectId);
        if (budgetMapper.selectCount(qw) > 0)
            throw new BizException("同年度同维度（项目/部门×科目）预算已存在，请勿重复编制");
    }

    /** 填充项目/部门名称（执行额由 BudExecService.fillAnnualActuals 批量填充，避免逐行重查询） */
    private void fillNames(BudBudget b) {
        if (b.getProjectId() != null && b.getProjectName() == null) {
            var pr = projectMapper.selectById(b.getProjectId());
            if (pr != null) b.setProjectName(pr.getName());
        }
        if (b.getDeptId() != null && b.getDeptName() == null) {
            var org = orgMapper.selectById(b.getDeptId());
            if (org != null) b.setDeptName(org.getName());
        }
    }

    private BudBudget require(Long id) {
        if (id == null) throw new BizException("缺少预算ID");
        BudBudget b = budgetMapper.selectById(id);
        if (b == null) throw new BizException("预算不存在");
        return b;
    }
}
