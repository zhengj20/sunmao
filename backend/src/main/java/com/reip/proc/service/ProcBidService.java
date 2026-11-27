package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.proc.entity.ProcBid;
import com.reip.proc.entity.ProcBidItem;
import com.reip.proc.entity.ProcCollusionWarn;
import com.reip.proc.entity.ProcTender;
import com.reip.proc.mapper.ProcMappers.BidItemMapper;
import com.reip.proc.mapper.ProcMappers.BidMapper;
import com.reip.proc.mapper.ProcMappers.CollusionWarnMapper;
import com.reip.proc.mapper.ProcMappers.MdmMapper;
import com.reip.proc.mapper.ProcMappers.TenderMapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 回标登记 + 清标（不平衡报价检测 / 围串标同IP预警）
 */
@Service
@RequiredArgsConstructor
public class ProcBidService {

    /** 不平衡报价判定系数：报价 > 全部报价均值 × 1.3 */
    private static final BigDecimal UNBALANCED_FACTOR = new BigDecimal("1.3");

    private final BidMapper bidMapper;
    private final TenderMapper tenderMapper;
    private final MdmMapper mdmMapper;
    private final CollusionWarnMapper collusionWarnMapper;
    private final BidItemMapper bidItemMapper;

    public List<ProcBid> list(Long tenderId) {
        return bidMapper.selectList(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getTenderId, tenderId).orderByAsc(ProcBid::getId));
    }

    // ================= 技术/商务标分离明细（proc_bid_item） =================

    public List<ProcBidItem> listItems(Long bidId) {
        detail(bidId);
        return bidItemMapper.selectList(new LambdaQueryWrapper<ProcBidItem>()
                .eq(ProcBidItem::getBidId, bidId).orderByAsc(ProcBidItem::getId));
    }

    private ProcBid detail(Long bidId) {
        ProcBid bid = bidMapper.selectById(bidId);
        if (bid == null) throw new BizException("回标记录不存在");
        return bid;
    }

    /** 新增明细：同回标内 itemCode 唯一；增后重算父 techScore */
    @Transactional
    public Long addItem(Long bidId, ProcBidItem item) {
        detail(bidId);
        if (item.getItemCode() == null || item.getItemCode().isBlank()) throw new BizException("缺少明细编码");
        Long dup = bidItemMapper.selectCount(new LambdaQueryWrapper<ProcBidItem>()
                .eq(ProcBidItem::getBidId, bidId).eq(ProcBidItem::getItemCode, item.getItemCode()));
        if (dup != null && dup > 0) throw new BizException("该回标已存在相同明细编码：" + item.getItemCode());
        item.setId(null);
        item.setBidId(bidId);
        if (item.getWeight() == null) item.setWeight(BigDecimal.ONE);
        if (item.getIsInvalid() == null) item.setIsInvalid(0);
        bidItemMapper.insert(item);
        recomputeBidTechScore(bidId);
        return item.getId();
    }

    /** 更新明细：必须属于该回标；改后重算父 techScore */
    @Transactional
    public void updateItem(Long bidId, ProcBidItem item) {
        detail(bidId);
        if (item.getId() == null) throw new BizException("缺少明细ID");
        ProcBidItem db = bidItemMapper.selectById(item.getId());
        if (db == null) throw new BizException("明细不存在");
        if (!bidId.equals(db.getBidId())) throw new BizException("明细不属于该回标");
        db.setItemCode(item.getItemCode() != null ? item.getItemCode() : db.getItemCode());
        db.setItemName(item.getItemName());
        db.setTechScore(item.getTechScore());
        db.setBizScore(item.getBizScore());
        db.setWeight(item.getWeight() != null ? item.getWeight() : BigDecimal.ONE);
        db.setIsInvalid(item.getIsInvalid() != null ? item.getIsInvalid() : 0);
        if (bidItemMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        recomputeBidTechScore(bidId);
    }

    /** 删除明细：必须属于该回标；删后重算父 techScore */
    @Transactional
    public void deleteItem(Long bidId, Long itemId) {
        detail(bidId);
        ProcBidItem db = bidItemMapper.selectById(itemId);
        if (db == null) throw new BizException("明细不存在");
        if (!bidId.equals(db.getBidId())) throw new BizException("明细不属于该回标");
        bidItemMapper.deleteById(itemId);
        recomputeBidTechScore(bidId);
    }

    /** 若该回标存在 bid_item 明细则按明细加权重算 techScore；返回是否已重算（无明细返回 false，供评标回退专家均值） */
    public boolean recomputeIfHasItems(Long bidId) {
        Long cnt = bidItemMapper.selectCount(new LambdaQueryWrapper<ProcBidItem>()
                .eq(ProcBidItem::getBidId, bidId));
        if (cnt == null || cnt == 0) return false;
        recomputeBidTechScore(bidId);
        return true;
    }

    /** 重算父 ProcBid.techScore = Σ(tech_score × weight)，仅统计有效项（is_invalid=0）；priceAmount 保持不变 */
    public void recomputeBidTechScore(Long bidId) {
        ProcBid bid = detail(bidId);
        List<ProcBidItem> items = bidItemMapper.selectList(new LambdaQueryWrapper<ProcBidItem>()
                .eq(ProcBidItem::getBidId, bidId));
        BigDecimal sum = items.stream()
                .filter(i -> i.getIsInvalid() == null || i.getIsInvalid() == 0)
                .map(i -> (i.getTechScore() == null ? BigDecimal.ZERO : i.getTechScore())
                        .multiply(i.getWeight() == null ? BigDecimal.ONE : i.getWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        bid.setTechScore(sum);
        if (bidMapper.updateById(bid) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 回标登记：开标后登记的直接置 OPENED */
    @Transactional
    public Long register(ProcBid bid) {
        if (bid.getTenderId() == null) throw new BizException("缺少发标ID");
        if (bid.getSupplierId() == null) throw new BizException("缺少供应商");
        ProcTender tender = tenderMapper.selectById(bid.getTenderId());
        if (tender == null) throw new BizException("发标记录不存在");
        if ("CLOSED".equals(tender.getStatus())) throw new BizException("发标已截止关闭，不能登记回标");
        Map<String, Object> supplier = mdmMapper.selectSupplier(bid.getSupplierId());
        if (supplier == null) throw new BizException("供应商不存在");
        Long cnt = bidMapper.selectCount(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getTenderId, bid.getTenderId())
                .eq(ProcBid::getSupplierId, bid.getSupplierId()));
        if (cnt != null && cnt > 0) throw new BizException("该供应商已回标，请勿重复登记");
        bid.setProcProjectId(tender.getProcProjectId());
        bid.setSupplierName((String) supplier.get("name"));
        bid.setUnbalancedFlag(0);
        bid.setBidStatus("OPENED".equals(tender.getStatus()) ? "OPENED" : "SUBMITTED");
        bidMapper.insert(bid);
        return bid.getId();
    }

    /**
     * 清标（对标源做机器检测）：
     * a) 不平衡报价：某家报价 > 全部报价均值×1.3 → unbalanced_flag=1；
     * b) 围串标：bid_ip 相同的不同供应商 → 写 proc_collusion_warn（rule_code=SAME_IP）
     */
    @Transactional
    public Map<String, Object> clear(Long tenderId) {
        ProcTender tender = tenderMapper.selectById(tenderId);
        if (tender == null) throw new BizException("发标记录不存在");
        List<ProcBid> bids = list(tenderId);
        if (bids.isEmpty()) throw new BizException("暂无回标记录，无法清标");

        // a) 不平衡报价检测
        BigDecimal sum = bids.stream().map(ProcBid::getPriceAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgPrice = sum.divide(BigDecimal.valueOf(bids.size()), 2, RoundingMode.HALF_UP);
        BigDecimal warnLine = avgPrice.multiply(UNBALANCED_FACTOR);
        List<Map<String, Object>> unbalanced = new ArrayList<>();
        for (ProcBid bid : bids) {
            int flag = (bid.getPriceAmount() != null && bid.getPriceAmount().compareTo(warnLine) > 0) ? 1 : 0;
            bid.setUnbalancedFlag(flag);
            if (bidMapper.updateById(bid) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
            if (flag == 1) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("supplierName", bid.getSupplierName());
                u.put("priceAmount", bid.getPriceAmount());
                u.put("avgPrice", avgPrice);
                unbalanced.add(u);
            }
        }

        // b) 围串标检测：同IP不同供应商（重复清标先清旧预警）
        collusionWarnMapper.delete(new LambdaQueryWrapper<ProcCollusionWarn>()
                .eq(ProcCollusionWarn::getProcProjectId, tender.getProcProjectId())
                .eq(ProcCollusionWarn::getRuleCode, "SAME_IP"));
        List<Map<String, Object>> warnings = new ArrayList<>();
        Map<String, List<ProcBid>> byIp = bids.stream()
                .filter(b -> b.getBidIp() != null && !b.getBidIp().isBlank())
                .collect(Collectors.groupingBy(ProcBid::getBidIp));
        for (Map.Entry<String, List<ProcBid>> en : byIp.entrySet()) {
            Set<Long> supplierIds = en.getValue().stream()
                    .map(ProcBid::getSupplierId).collect(Collectors.toSet());
            if (supplierIds.size() >= 2) {
                String names = en.getValue().stream().map(ProcBid::getSupplierName)
                        .distinct().collect(Collectors.joining("、"));
                ProcCollusionWarn warn = new ProcCollusionWarn();
                warn.setProcProjectId(tender.getProcProjectId());
                warn.setRuleCode("SAME_IP");
                warn.setDetail("回标IP相同（" + en.getKey() + "）：" + names);
                warn.setRiskLevel("MEDIUM");
                warn.setCreatedAt(LocalDateTime.now());
                collusionWarnMapper.insert(warn);
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("ruleCode", "SAME_IP");
                w.put("ip", en.getKey());
                w.put("supplierNames", names);
                w.put("riskLevel", "MEDIUM");
                warnings.add(w);
            }
        }

        // 检测报告
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tenderId", tenderId);
        report.put("procProjectId", tender.getProcProjectId());
        report.put("bidCount", bids.size());
        report.put("avgPrice", avgPrice);
        report.put("unbalancedFactor", UNBALANCED_FACTOR);
        report.put("unbalancedCount", unbalanced.size());
        report.put("unbalancedBids", unbalanced);
        report.put("collusionWarnCount", warnings.size());
        report.put("collusionWarnings", warnings);
        return report;
    }
}
