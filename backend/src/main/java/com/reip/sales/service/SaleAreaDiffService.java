package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.sales.entity.SaleAreaDiff;
import com.reip.sales.entity.SalePaymentPlan;
import com.reip.sales.mapper.SaleMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面积补差（多退少补）：导入/手工登记 合同面积 vs 实测面积 → 按单价生成差价款
 * 少补(COLLECT)生成应收计划纳入回款；多退(REFUND)生成退款待办。
 */
@Service
@RequiredArgsConstructor
public class SaleAreaDiffService {

    private final SaleMappers.AreaDiffMapper diffMapper;
    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.PaymentPlanMapper planMapper;
    private final SaleMappers.MdmLookupMapper mdm;
    private final SaleMappers.MatchMapper matchMapper;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.03");

    // ==================== 查询 ====================

    public PageResult<SaleAreaDiff> page(long current, long size, Long projectId, String status,
                                         String direction, String keyword) {
        LambdaQueryWrapper<SaleAreaDiff> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleAreaDiff::getProjectId, projectId);
        if (status != null && !status.isBlank()) qw.eq(SaleAreaDiff::getStatus, status);
        if (direction != null && !direction.isBlank()) qw.eq(SaleAreaDiff::getDirection, direction);
        if (keyword != null && !keyword.isBlank())
            qw.and(w -> w.like(SaleAreaDiff::getRoomCode, keyword)
                    .or().like(SaleAreaDiff::getCustomerName, keyword)
                    .or().like(SaleAreaDiff::getDiffNo, keyword));
        qw.orderByDesc(SaleAreaDiff::getId);
        Page<SaleAreaDiff> p = diffMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Map<String, Object> stats(Long projectId) {
        List<SaleAreaDiff> all = diffMapper.selectList(new LambdaQueryWrapper<SaleAreaDiff>()
                .eq(projectId != null, SaleAreaDiff::getProjectId, projectId));
        BigDecimal pendingCollect = BigDecimal.ZERO, pendingRefund = BigDecimal.ZERO;
        BigDecimal settledCollect = BigDecimal.ZERO, settledRefund = BigDecimal.ZERO;
        int pendingCount = 0, settledCount = 0;
        for (SaleAreaDiff d : all) {
            BigDecimal amt = SaleSupport.nvl(d.getDiffAmount());
            boolean collect = "COLLECT".equals(d.getDirection());
            if ("PENDING".equals(d.getStatus()) || "GENERATED".equals(d.getStatus())) {
                if (collect) pendingCollect = pendingCollect.add(amt.abs()); else pendingRefund = pendingRefund.add(amt.abs());
                if ("PENDING".equals(d.getStatus())) pendingCount++;
            } else if ("SETTLED".equals(d.getStatus())) {
                if (collect) settledCollect = settledCollect.add(amt.abs()); else settledRefund = settledRefund.add(amt.abs());
                settledCount++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingCollect", pendingCollect);
        m.put("pendingRefund", pendingRefund);
        m.put("settledCollect", settledCollect);
        m.put("settledRefund", settledRefund);
        m.put("pendingCount", pendingCount);
        m.put("settledCount", settledCount);
        m.put("total", new BigDecimal(all.size()));
        return m;
    }

    public SaleAreaDiff detail(Long id) {
        SaleAreaDiff d = diffMapper.selectById(id);
        if (d == null) throw new BizException("补差记录不存在");
        return d;
    }

    // ==================== 登记（手工 / 导入） ====================

    @Transactional
    public SaleAreaDiff create(CreateForm form) {
        if (form == null || form.getProjectId() == null) throw new BizException("请选择项目");
        if (form.getRoomCode() == null || form.getRoomCode().isBlank()) throw new BizException("请填写房号");
        SaleAreaDiff d = buildFromRoom(form.getProjectId(), form.getRoomCode().trim(),
                form.getContractArea(), form.getMeasuredArea(), form.getUnitPrice(), "MANUAL", null, form.getRemark());
        diffMapper.insert(d);
        return d;
    }

    /** CSV 导入：每行 房号,合同面积,实测面积,单价（单价可空=按成交价折算；合同面积可空=按房间面积） */
    public Map<String, Object> importCsv(Long projectId, String csv, String remark) {
        if (projectId == null) throw new BizException("请选择项目");
        if (csv == null || csv.isBlank()) throw new BizException("请填写导入内容");
        String batchNo = "BC" + System.currentTimeMillis();
        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        String[] lines = csv.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("房号") || line.startsWith("#")) continue; // 表头
            String[] c = splitCsv(line);
            if (c.length < 3) { skipped++; errors.add("列数不足(需≥3)：" + line); continue; }
            String roomCode = c[0].trim();
            BigDecimal contractArea = parse(c.length > 1 ? c[1] : null);
            BigDecimal measuredArea = parse(c.length > 2 ? c[2] : null);
            BigDecimal unitPrice = parse(c.length > 3 ? c[3] : null);
            if (measuredArea == null || measuredArea.signum() <= 0) { skipped++; errors.add("实测面积无效：" + line); continue; }
            try {
                SaleAreaDiff d = buildFromRoom(projectId, roomCode, contractArea, measuredArea, unitPrice, "IMPORT", batchNo, remark);
                diffMapper.insert(d);
                imported++;
            } catch (BizException e) {
                skipped++; errors.add(e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("batchNo", batchNo);
        r.put("imported", imported);
        r.put("skipped", skipped);
        r.put("errors", errors);
        return r;
    }

    // ==================== 结算（多退少补） ====================

    /** 生成补差单：少补(COLLECT)→生成应收计划纳入回款；多退(REFUND)→标记待退款 */
    @Transactional
    public SaleAreaDiff settle(Long id) {
        SaleAreaDiff d = detail(id);
        if (!"PENDING".equals(d.getStatus())) throw new BizException("仅待结算的补差可生成补差单");
        if ("NONE".equals(d.getDirection()) || SaleSupport.nvl(d.getDiffAmount()).signum() == 0)
            throw new BizException("无差异金额，无需生成补差单");
        if ("COLLECT".equals(d.getDirection()) && d.getContractId() != null) {
            SalePaymentPlan p = new SalePaymentPlan();
            p.setContractId(d.getContractId());
            p.setSeq(99);
            p.setTermDesc("面积补差(少补款)");
            p.setPlanAmount(d.getDiffAmount().abs());
            p.setReceivedAmount(BigDecimal.ZERO);
            p.setDueDate(LocalDate.now().plusDays(30));
            p.setStatus("DUE");
            planMapper.insert(p);
            d.setPlanId(p.getId());
        }
        d.setStatus("GENERATED");
        diffMapper.updateById(d);
        return d;
    }

    /** 标记已结清（已收款/已退款） */
    @Transactional
    public void settleDone(Long id, BigDecimal settled) {
        SaleAreaDiff d = detail(id);
        if ("SETTLED".equals(d.getStatus())) throw new BizException("该补差已结清");
        d.setSettleAmount(settled == null ? d.getDiffAmount().abs() : settled);
        d.setStatus("SETTLED");
        diffMapper.updateById(d);
    }

    @Transactional
    public void delete(Long id) {
        SaleAreaDiff d = detail(id);
        if ("SETTLED".equals(d.getStatus())) throw new BizException("已结清的补差不可删除");
        diffMapper.deleteById(id);
    }

    // ==================== 私有 ====================

    /** 依据房号带出房间/合同/客户，计算面积差与差价款 */
    private SaleAreaDiff buildFromRoom(Long projectId, String roomCode, BigDecimal contractArea,
                                       BigDecimal measuredArea, BigDecimal unitPrice, String source,
                                       String batchNo, String remark) {
        Map<String, Object> room = mdm.selectRoomByCode(roomCode);
        if (room == null) throw new BizException("房间不存在：" + roomCode);
        Long roomId = ((Number) room.get("id")).longValue();
        BigDecimal defaultContractArea = toBd(room.get("area"));
        if (measuredArea == null || measuredArea.signum() <= 0) throw new BizException("实测面积无效：" + roomCode);
        if (contractArea == null || contractArea.signum() <= 0) contractArea = defaultContractArea;

        // 带出最近一份生效合同（成交价→默认单价）
        Map<String, Object> contract = matchMapper.selectContractByRoom(roomId);
        Long contractId = null;
        BigDecimal contractPrice = null;
        if (contract != null) {
            contractId = ((Number) contract.get("contractId")).longValue();
            contractPrice = toBd(contract.get("contractPrice"));
        }

        if (unitPrice == null || unitPrice.signum() <= 0) {
            if (contractPrice != null && contractPrice.signum() > 0)
                unitPrice = contractPrice.divide(contractArea, 2, RoundingMode.HALF_UP);
            else unitPrice = BigDecimal.ZERO;
        }

        BigDecimal diffArea = measuredArea.subtract(contractArea);
        BigDecimal diffAmount = diffArea.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        String direction = diffAmount.signum() > 0 ? "COLLECT" : (diffAmount.signum() < 0 ? "REFUND" : "NONE");
        boolean within = contractArea.signum() > 0
                && diffArea.abs().divide(contractArea, 4, RoundingMode.HALF_UP).compareTo(DEFAULT_THRESHOLD) <= 0;

        SaleAreaDiff d = new SaleAreaDiff();
        d.setProjectId(projectId);
        d.setDiffNo(codeRuleService.next("MC", support.codeCtx(projectId)));
        d.setRoomId(roomId);
        d.setRoomCode(roomCode);
        d.setContractId(contractId);
        d.setContractArea(contractArea);
        d.setMeasuredArea(measuredArea);
        d.setDiffArea(diffArea);
        d.setUnitPrice(unitPrice);
        d.setDiffAmount(diffAmount);
        d.setDirection(direction);
        d.setThreshold(DEFAULT_THRESHOLD);
        d.setWithinTolerance(within);
        d.setSource(source);
        d.setBatchNo(batchNo);
        d.setStatus("PENDING");
        d.setSettleAmount(BigDecimal.ZERO);
        d.setRemark(remark);
        // 客户名带出
        if (contract != null && contract.get("customerName") != null) {
            d.setCustomerName(String.valueOf(contract.get("customerName")));
            if (contract.get("customerId") != null) d.setCustomerId(((Number) contract.get("customerId")).longValue());
        }
        return d;
    }

    private String[] splitCsv(String line) {
        String sep = line.contains(",") ? "," : (line.contains("\t") ? "\t" : ",");
        return line.split(java.util.regex.Pattern.quote(sep));
    }

    private BigDecimal parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try { return new BigDecimal(t); } catch (Exception e) { throw new BizException("数值无效：" + s); }
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }

    @Data
    public static class CreateForm {
        private Long projectId;
        private String roomCode;
        private BigDecimal contractArea;
        private BigDecimal measuredArea;
        private BigDecimal unitPrice;
        private String remark;
    }
}
