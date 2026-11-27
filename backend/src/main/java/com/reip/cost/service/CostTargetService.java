package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostTargetItem;
import com.reip.cost.entity.CostTargetVersion;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 目标成本：版本管理 / 明细测算 / 提交审批 / 锁定 / 版本对比
 */
@Service
@RequiredArgsConstructor
public class CostTargetService {

    private final CostMappers.TargetVersionMapper versionMapper;
    private final CostMappers.TargetItemMapper itemMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final CostSupport support;

    // ==================== 查询 ====================

    public PageResult<CostTargetVersion> page(long current, long size, Long projectId, String stage,
                                              String status, String kw) {
        LambdaQueryWrapper<CostTargetVersion> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostTargetVersion::getProjectId, projectId);
        if (stage != null && !stage.isBlank()) qw.eq(CostTargetVersion::getStage, stage);
        if (status != null && !status.isBlank()) qw.eq(CostTargetVersion::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.like(CostTargetVersion::getVersionCode, kw);
        qw.orderByDesc(CostTargetVersion::getId);
        Page<CostTargetVersion> p = versionMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostTargetVersion detail(Long id) {
        CostTargetVersion v = require(id);
        v.setItems(itemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getVersionId, id).orderByAsc(CostTargetItem::getAccountId)));
        return v;
    }

    public List<CostTargetItem> items(Long versionId) {
        require(versionId);
        return itemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getVersionId, versionId).orderByAsc(CostTargetItem::getAccountId));
    }

    // ==================== 版本 CRUD ====================

    @Transactional
    public Long create(CostTargetVersion version, List<CostTargetItem> items) {
        if (version == null) throw new BizException("缺少表单内容");
        if (version.getProjectId() == null) throw new BizException("请选择项目");
        Map<String, String> ctx = support.codeCtx(version.getProjectId()); // 校验项目存在
        version.setId(null);
        version.setVersionCode(codeRuleService.next("MB", ctx));
        version.setStatus("DRAFT");
        List<CostTargetItem> normalized = new ArrayList<>();
        if (items != null) items.forEach(i -> normalized.add(normalizeItem(i, version.getBuildArea())));
        version.setTotalAmount(sumAmount(normalized));
        versionMapper.insert(version);
        for (CostTargetItem item : normalized) {
            item.setId(null);
            item.setVersionId(version.getId());
            itemMapper.insert(item);
        }
        return version.getId();
    }

    @Transactional
    public void update(CostTargetVersion version) {
        if (version == null || version.getId() == null) throw new BizException("缺少版本ID");
        CostTargetVersion db = require(version.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的目标成本可修改");
        version.setVersionCode(null); // 编号不可修改
        version.setStatus(db.getStatus());
        int updated = versionMapper.updateById(version);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        // 建筑面积变化需重算全部明细单方成本
        boolean areaChanged = version.getBuildArea() != null
                && version.getBuildArea().compareTo(CostSupport.nvl(db.getBuildArea())) != 0;
        if (areaChanged) refreshUnitCost(version.getId());
    }

    @Transactional
    public void delete(Long id) {
        CostTargetVersion db = require(id);
        if ("LOCKED".equals(db.getStatus())) throw new BizException("目标成本已锁定，禁止删除");
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态的目标成本可删除");
        versionMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<CostTargetItem>().eq(CostTargetItem::getVersionId, id));
    }

    // ==================== 明细 CRUD ====================

    @Transactional
    public Long addItem(Long versionId, CostTargetItem item) {
        CostTargetVersion v = requireDraft(versionId);
        if (item == null) throw new BizException("缺少明细内容");
        item.setId(null);
        item.setVersionId(versionId);
        normalizeItem(item, v.getBuildArea());
        itemMapper.insert(item);
        refreshTotal(versionId);
        return item.getId();
    }

    @Transactional
    public void updateItem(Long versionId, CostTargetItem item) {
        if (item == null || item.getId() == null) throw new BizException("缺少明细ID");
        CostTargetItem db = itemMapper.selectById(item.getId());
        if (db == null || !db.getVersionId().equals(versionId)) throw new BizException("目标成本明细不存在");
        CostTargetVersion v = requireDraft(versionId);
        item.setVersionId(versionId);
        normalizeItem(item, v.getBuildArea());
        int updated = itemMapper.updateById(item);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        refreshTotal(versionId);
    }

    @Transactional
    public void deleteItem(Long versionId, Long itemId) {
        requireDraft(versionId);
        int deleted = itemMapper.delete(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getId, itemId).eq(CostTargetItem::getVersionId, versionId));
        if (deleted == 0) throw new BizException("目标成本明细不存在");
        refreshTotal(versionId);
    }

    // ==================== 审批 / 锁定 ====================

    /** 提交审批（bizType=COST_TARGET） */
    @Transactional
    public void submit(Long id) {
        CostTargetVersion v = require(id);
        if (!"DRAFT".equals(v.getStatus())) throw new BizException("仅草稿状态的目标成本可提交审批");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("versionCode", v.getVersionCode());
        snapshot.put("stage", v.getStage());
        snapshot.put("totalAmount", CostSupport.nvl(v.getTotalAmount()));
        snapshot.put("projectId", v.getProjectId());
        workflowClient.start("COST_TARGET", id, "目标成本 " + v.getVersionCode(),
                CostSupport.nvl(v.getTotalAmount()), snapshot);
        CostTargetVersion up = new CostTargetVersion();
        up.setId(id);
        up.setVersion(v.getVersion());
        up.setStatus("APPROVING");
        if (versionMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 锁定（仅审批通过后可锁定，锁定后禁改禁删） */
    @Transactional
    public void lock(Long id) {
        CostTargetVersion v = require(id);
        if (!"APPROVED".equals(v.getStatus())) throw new BizException("仅审批通过的目标成本可锁定");
        CostTargetVersion up = new CostTargetVersion();
        up.setId(id);
        up.setVersion(v.getVersion());
        up.setStatus("LOCKED");
        if (versionMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 工作流回调：审批通过 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostTargetVersion v = versionMapper.selectById(id);
        if (v == null || !"APPROVING".equals(v.getStatus())) return; // 幂等：非在途状态忽略
        CostTargetVersion up = new CostTargetVersion();
        up.setId(id);
        up.setVersion(v.getVersion());
        up.setStatus("APPROVED");
        versionMapper.updateById(up);
    }

    /** 工作流回调：驳回（退回 DRAFT 可改后重提） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostTargetVersion v = versionMapper.selectById(id);
        if (v == null || !"APPROVING".equals(v.getStatus())) return;
        CostTargetVersion up = new CostTargetVersion();
        up.setId(id);
        up.setVersion(v.getVersion());
        up.setStatus("DRAFT");
        versionMapper.updateById(up);
    }

    // ==================== 版本对比 ====================

    /** 版本对比：按科目聚合两版本金额，diff=B-A，diffRate=diff/A*100 */
    public Map<String, Object> compare(Long versionA, Long versionB) {
        CostTargetVersion a = require(versionA);
        CostTargetVersion b = require(versionB);
        Map<Long, CostTargetItem> itemsA = loadItems(versionA);
        Map<Long, CostTargetItem> itemsB = loadItems(versionB);
        Set<Long> accountIds = new LinkedHashSet<>();
        accountIds.addAll(itemsA.keySet());
        accountIds.addAll(itemsB.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalA = BigDecimal.ZERO, totalB = BigDecimal.ZERO;
        for (Long accountId : accountIds) {
            CostTargetItem ia = itemsA.get(accountId), ib = itemsB.get(accountId);
            BigDecimal amountA = ia == null ? BigDecimal.ZERO : CostSupport.nvl(ia.getAmount());
            BigDecimal amountB = ib == null ? BigDecimal.ZERO : CostSupport.nvl(ib.getAmount());
            totalA = totalA.add(amountA);
            totalB = totalB.add(amountB);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", accountId);
            row.put("accountCode", ia != null ? ia.getAccountCode() : (ib == null ? null : ib.getAccountCode()));
            row.put("accountName", ia != null ? ia.getAccountName() : (ib == null ? null : ib.getAccountName()));
            row.put("amountA", amountA);
            row.put("amountB", amountB);
            row.put("diff", amountB.subtract(amountA));
            row.put("diffRate", amountA.compareTo(BigDecimal.ZERO) == 0 ? null
                    : amountB.subtract(amountA).multiply(BigDecimal.valueOf(100))
                            .divide(amountA, 2, RoundingMode.HALF_UP));
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalA", totalA);
        summary.put("totalB", totalB);
        summary.put("totalDiff", totalB.subtract(totalA));
        summary.put("totalDiffRate", totalA.compareTo(BigDecimal.ZERO) == 0 ? null
                : totalB.subtract(totalA).multiply(BigDecimal.valueOf(100)).divide(totalA, 2, RoundingMode.HALF_UP));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionA", Map.of("id", a.getId(), "versionCode", a.getVersionCode()));
        result.put("versionB", Map.of("id", b.getId(), "versionCode", b.getVersionCode()));
        result.put("items", rows);
        result.put("summary", summary);
        return result;
    }

    // ==================== Excel 导入导出 ====================

    /**
     * 导出目标成本明细为 Excel 数据（返回 List<Map> 供 EasyExcel 写入）
     */
    public List<Map<String, Object>> exportExcel(Long versionId) {
        require(versionId);
        List<CostTargetItem> items = items(versionId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CostTargetItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("科目编码", item.getAccountCode());
            row.put("科目名称", item.getAccountName());
            row.put("工程量", item.getQuantity());
            row.put("单位", item.getUnit());
            row.put("单价", item.getUnitPrice());
            row.put("金额", item.getAmount());
            row.put("单方成本", item.getUnitCost());
            row.put("指标来源", item.getIndicatorRef());
            row.put("备注", item.getRemark());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 导入 Excel 数据到目标成本明细（覆盖式：先删后插）
     */
    @Transactional
    public int importExcel(Long versionId, List<Map<String, String>> excelRows) {
        CostTargetVersion v = requireDraft(versionId);
        if (excelRows == null || excelRows.isEmpty()) throw new BizException("导入数据为空");
        // 清空原有明细
        itemMapper.delete(new LambdaQueryWrapper<CostTargetItem>().eq(CostTargetItem::getVersionId, versionId));
        int count = 0;
        for (Map<String, String> row : excelRows) {
            String accountCode = row.get("科目编码");
            if (accountCode == null || accountCode.isBlank()) continue;
            // 查找科目
            CostMappers.AccountRef acc = support.accountByCode(accountCode);
            if (acc == null) continue;
            CostTargetItem item = new CostTargetItem();
            item.setVersionId(versionId);
            item.setAccountId(acc.getId());
            item.setAccountCode(acc.getCode());
            item.setAccountName(acc.getName());
            item.setQuantity(parseBigDecimal(row.get("工程量")));
            item.setUnit(row.get("单位"));
            item.setUnitPrice(parseBigDecimal(row.get("单价")));
            item.setIndicatorRef(row.get("指标来源"));
            item.setRemark(row.get("备注"));
            normalizeItem(item, v.getBuildArea());
            itemMapper.insert(item);
            count++;
        }
        refreshTotal(versionId);
        return count;
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 私有方法 ====================

    private CostTargetVersion require(Long id) {
        if (id == null) throw new BizException("缺少版本ID");
        CostTargetVersion v = versionMapper.selectById(id);
        if (v == null) throw new BizException("目标成本版本不存在");
        return v;
    }

    private CostTargetVersion requireDraft(Long id) {
        CostTargetVersion v = require(id);
        if ("LOCKED".equals(v.getStatus())) throw new BizException("目标成本已锁定，禁止修改明细");
        if (!"DRAFT".equals(v.getStatus())) throw new BizException("仅草稿状态的目标成本可维护明细");
        return v;
    }

    /** 明细标准化：带出科目编码/名称，金额=量×价，单方=金额/建筑面积 */
    private CostTargetItem normalizeItem(CostTargetItem item, BigDecimal buildArea) {
        CostMappers.AccountRef acc = support.account(item.getAccountId());
        item.setAccountCode(acc.getCode());
        item.setAccountName(acc.getName());
        BigDecimal quantity = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        item.setAmount(amount);
        if (buildArea != null && buildArea.compareTo(BigDecimal.ZERO) > 0) {
            item.setUnitCost(amount.divide(buildArea, 2, RoundingMode.HALF_UP));
        }
        return item;
    }

    private BigDecimal sumAmount(List<CostTargetItem> items) {
        return items.stream().map(i -> CostSupport.nvl(i.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** 明细变动后重算版本总额 */
    private void refreshTotal(Long versionId) {
        CostTargetVersion v = require(versionId);
        List<CostTargetItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getVersionId, versionId));
        CostTargetVersion up = new CostTargetVersion();
        up.setId(versionId);
        up.setVersion(v.getVersion());
        up.setTotalAmount(sumAmount(items));
        if (versionMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 建筑面积变更后重算全部明细单方成本 */
    private void refreshUnitCost(Long versionId) {
        CostTargetVersion v = require(versionId);
        if (v.getBuildArea() == null || v.getBuildArea().compareTo(BigDecimal.ZERO) <= 0) return;
        List<CostTargetItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                .eq(CostTargetItem::getVersionId, versionId));
        for (CostTargetItem item : items) {
            CostTargetItem up = new CostTargetItem();
            up.setId(item.getId());
            up.setVersion(item.getVersion());
            up.setUnitCost(CostSupport.nvl(item.getAmount())
                    .divide(v.getBuildArea(), 2, RoundingMode.HALF_UP));
            itemMapper.updateById(up);
        }
    }

    private Map<Long, CostTargetItem> loadItems(Long versionId) {
        Map<Long, CostTargetItem> map = new LinkedHashMap<>();
        itemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                        .eq(CostTargetItem::getVersionId, versionId))
                .forEach(i -> map.merge(i.getAccountId(), i, (a, b) -> a)); // 同科目聚合取首条（正常一对一）
        return map;
    }
}
