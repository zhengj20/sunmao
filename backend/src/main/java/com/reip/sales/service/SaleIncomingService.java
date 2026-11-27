package com.reip.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.sales.entity.SaleIncoming;
import com.reip.sales.entity.SalePaymentPlan;
import com.reip.sales.mapper.SaleMappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 来账（银企直联 / 手机短信）：收款信息登记 → 智能/手动匹配到合同·房间 → 核销生成实收。
 * 银企直联/短信推送用"模拟接收"演示（按未收应收自动生成来账），匹配后复用 SalePaymentService 核销。
 */
@Service
@RequiredArgsConstructor
public class SaleIncomingService {

    private final SaleMappers.IncomingMapper incomingMapper;
    private final SaleMappers.ContractMapper contractMapper;
    private final SaleMappers.PaymentPlanMapper planMapper;
    private final SaleMappers.MatchMapper matchMapper;
    private final SalePaymentService paymentService;
    private final SaleSupport support;
    private final CodeRuleService codeRuleService;

    // ==================== 查询 ====================

    public PageResult<SaleIncoming> page(long current, long size, Long projectId, String matchStatus, String source) {
        LambdaQueryWrapper<SaleIncoming> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(SaleIncoming::getProjectId, projectId);
        if (matchStatus != null && !matchStatus.isBlank()) qw.eq(SaleIncoming::getMatchStatus, matchStatus);
        if (source != null && !source.isBlank()) qw.eq(SaleIncoming::getSource, source);
        qw.orderByDesc(SaleIncoming::getId);
        Page<SaleIncoming> p = incomingMapper.selectPage(new Page<>(current, size), qw);
        p.getRecords().forEach(this::fill);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Map<String, Object> stats(Long projectId) {
        List<SaleIncoming> all = incomingMapper.selectList(new LambdaQueryWrapper<SaleIncoming>()
                .eq(projectId != null, SaleIncoming::getProjectId, projectId));
        BigDecimal pendingAmt = BigDecimal.ZERO, matchedAmt = BigDecimal.ZERO;
        int pendingCount = 0, matchedCount = 0;
        for (SaleIncoming i : all) {
            if ("PENDING".equals(i.getMatchStatus())) { pendingAmt = pendingAmt.add(i.getAmount()); pendingCount++; }
            else if ("MATCHED".equals(i.getMatchStatus())) { matchedAmt = matchedAmt.add(i.getAmount()); matchedCount++; }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingAmt", pendingAmt);
        m.put("pendingCount", pendingCount);
        m.put("matchedAmt", matchedAmt);
        m.put("matchedCount", matchedCount);
        m.put("total", new BigDecimal(all.size()));
        return m;
    }

    // ==================== 登记 / 模拟推送 ====================

    @Transactional
    public SaleIncoming create(CreateForm form) {
        if (form == null || form.getProjectId() == null) throw new BizException("请选择项目");
        if (form.getAmount() == null || form.getAmount().signum() <= 0) throw new BizException("请填写来账金额");
        SaleIncoming i = new SaleIncoming();
        i.setIncomingNo(codeRuleService.next("LZ", support.codeCtx(form.getProjectId())));
        i.setProjectId(form.getProjectId());
        i.setSource(form.getSource() == null || form.getSource().isBlank() ? "BANK" : form.getSource());
        i.setPayerName(form.getPayerName());
        i.setPayerAccount(form.getPayerAccount());
        i.setAmount(form.getAmount());
        i.setBankSerial(form.getBankSerial());
        i.setReceivedAt(form.getReceivedAt() == null ? LocalDateTime.now() : form.getReceivedAt());
        i.setMatchStatus("PENDING");
        i.setRemark(form.getRemark());
        incomingMapper.insert(i);
        return i;
    }

    /**
     * 模拟银企直联/短信推送：扫描项目下未收应收，按付款人(客户名)+金额生成待匹配来账，
     * 用于演示"银行/短信收款信息自动到达并可匹配到房间"。返回生成笔数。
     */
    @Transactional
    public int simulate(Long projectId, String source, int max) {
        if (projectId == null) throw new BizException("请选择项目");
        List<SaleIncoming> pending = incomingMapper.selectList(new LambdaQueryWrapper<SaleIncoming>()
                .eq(SaleIncoming::getProjectId, projectId).eq(SaleIncoming::getMatchStatus, "PENDING"));
        if (!pending.isEmpty()) throw new BizException("请先处理现有待匹配来账，再模拟接收");
        List<SalePaymentPlan> duePlans = planMapper.selectList(new LambdaQueryWrapper<SalePaymentPlan>()
                .in(SalePaymentPlan::getStatus, "DUE", "OVERDUE"));
        int n = 0;
        int limit = max <= 0 ? 5 : max;
        for (SalePaymentPlan p : duePlans) {
            if (n >= limit) break;
            var c = contractMapper.selectById(p.getContractId());
            if (c == null || c.getProjectId() == null || !projectId.equals(c.getProjectId())) continue;
            if (!"ACTIVE".equals(c.getStatus()) && !"FILED".equals(c.getStatus())) continue;
            BigDecimal remaining = SaleSupport.nvl(p.getPlanAmount()).subtract(SaleSupport.nvl(p.getReceivedAmount()));
            if (remaining.signum() <= 0) continue;
            SaleIncoming i = new SaleIncoming();
            i.setIncomingNo(codeRuleService.next("LZ", support.codeCtx(projectId)));
            i.setProjectId(projectId);
            i.setSource(source == null || source.isBlank() ? "BANK" : source);
            i.setPayerName(support.customerName(c.getCustomerId()));
            i.setPayerAccount("6222****" + (1000 + n));
            i.setAmount(remaining.setScale(2, RoundingMode.HALF_UP));
            i.setBankSerial((source != null && source.startsWith("SMS") ? "SMS" : "BANK")
                    + System.currentTimeMillis() + n);
            i.setReceivedAt(LocalDateTime.now());
            i.setMatchStatus("PENDING");
            i.setRemark("模拟" + ("SMS".equals(i.getSource()) ? "手机短信" : "银企直联") + "推送");
            incomingMapper.insert(i);
            n++;
        }
        return n;
    }

    // ==================== 匹配 ====================

    /** 智能匹配建议：按付款人(客户名)+金额+房号 从生效合同中推荐最匹配的一条 */
    public Map<String, Object> suggest(Long id) {
        SaleIncoming i = require(id);
        List<Map<String, Object>> candidates = matchMapper.selectMatchCandidates(i.getProjectId());
        String payer = i.getPayerName() == null ? "" : i.getPayerName().trim();
        String serial = (i.getBankSerial() == null ? "" : i.getBankSerial()) + " " + (i.getRemark() == null ? "" : i.getRemark());
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> cand : candidates) {
            if (cand.get("contractId") == null) continue;
            Long contractId = ((Number) cand.get("contractId")).longValue();
            String customer = cand.get("customerName") == null ? "" : String.valueOf(cand.get("customerName"));
            String roomCode = cand.get("roomCode") == null ? "" : String.valueOf(cand.get("roomCode"));
            boolean nameHit = !payer.isEmpty() && (customer.contains(payer) || payer.contains(customer));
            boolean roomHit = roomCode != null && !roomCode.isBlank() && serial.contains(roomCode);
            if (!nameHit && !roomHit) continue;
            BigDecimal outstanding = outstandingOf(contractId);
            if (outstanding.signum() <= 0) continue;
            int score = (roomHit ? 100 : 0) + (nameHit ? 10 : 0)
                    + (outstanding.subtract(i.getAmount()).abs().compareTo(outstanding) < 0 ? 5 : 0);
            Map<String, Object> m = new LinkedHashMap<>(cand);
            m.put("outstanding", outstanding);
            m.put("score", score);
            m.put("suggestedPlanId", earliestDuePlan(contractId));
            scored.add(m);
        }
        scored.sort((a, b) -> ((Number) b.get("score")).intValue() - ((Number) a.get("score")).intValue());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incoming", i);
        result.put("candidates", scored);
        result.put("best", scored.isEmpty() ? null : scored.get(0));
        return result;
    }

    /** 匹配（手动指定合同或自动取最佳）→ 核销生成实收 */
    @Transactional
    public SaleIncoming match(Long id, Long contractId) {
        SaleIncoming i = require(id);
        if ("MATCHED".equals(i.getMatchStatus())) throw new BizException("该来账已匹配");
        if (contractId == null) {
            Map<String, Object> sug = suggest(id);
            Map<String, Object> best = (Map<String, Object>) sug.get("best");
            if (best == null) throw new BizException("未找到匹配的合同，请手动选择合同");
            contractId = ((Number) best.get("contractId")).longValue();
        }
        var c = contractMapper.selectById(contractId);
        if (c == null || c.getProjectId() == null || !c.getProjectId().equals(i.getProjectId()))
            throw new BizException("合同不存在或不属于该项目");
        Long receiptId = paymentService.receiveSpan(contractId, i.getAmount(), "转账", i.getBankSerial(), i.getReceivedAt());
        i.setContractId(contractId);
        i.setRoomId(c.getRoomId());
        i.setRoomCode(support.roomCode(c.getRoomId()));
        i.setReceiptId(receiptId);
        i.setMatchStatus("MATCHED");
        i.setMatchAt(LocalDateTime.now());
        incomingMapper.updateById(i);
        return i;
    }

    /** 排除（无法匹配/非本司来账） */
    @Transactional
    public void reject(Long id) {
        SaleIncoming i = require(id);
        i.setMatchStatus("REJECTED");
        i.setMatchAt(LocalDateTime.now());
        incomingMapper.updateById(i);
    }

    // ==================== 私有 ====================

    private SaleIncoming require(Long id) {
        SaleIncoming i = incomingMapper.selectById(id);
        if (i == null) throw new BizException("来账记录不存在");
        return i;
    }

    private void fill(SaleIncoming i) {
        if (i.getContractId() != null) {
            var c = contractMapper.selectById(i.getContractId());
            if (c != null) {
                i.setContractNo(c.getContractNo());
                if (i.getRoomCode() == null) i.setRoomCode(support.roomCode(c.getRoomId()));
            }
        }
        if (i.getPayerName() == null) i.setPayerName("-");
    }

    private BigDecimal outstandingOf(Long contractId) {
        List<SalePaymentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId).in(SalePaymentPlan::getStatus, "DUE", "OVERDUE"));
        BigDecimal sum = BigDecimal.ZERO;
        for (SalePaymentPlan p : plans) sum = sum.add(SaleSupport.nvl(p.getPlanAmount()).subtract(SaleSupport.nvl(p.getReceivedAmount())));
        return sum;
    }

    private Long earliestDuePlan(Long contractId) {
        SalePaymentPlan p = planMapper.selectOne(new LambdaQueryWrapper<SalePaymentPlan>()
                .eq(SalePaymentPlan::getContractId, contractId).in(SalePaymentPlan::getStatus, "DUE", "OVERDUE")
                .orderByAsc(SalePaymentPlan::getDueDate).orderByAsc(SalePaymentPlan::getSeq).last("LIMIT 1"));
        return p == null ? null : p.getId();
    }

    @Data
    public static class CreateForm {
        private Long projectId;
        private String source;
        private String payerName;
        private String payerAccount;
        private BigDecimal amount;
        private String bankSerial;
        private LocalDateTime receivedAt;
        private String remark;
    }
}
