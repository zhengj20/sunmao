package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.framework.param.ParamService;
import com.reip.proc.entity.*;
import com.reip.proc.mapper.ProcMappers.BidMapper;
import com.reip.proc.mapper.ProcMappers.EvalDetailMapper;
import com.reip.proc.mapper.ProcMappers.EvaluationMapper;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.proc.mapper.ProcMappers.TenderMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评标：专家评分留痕、总分=技术×技术权重/100+商务×商务权重/100（商务缺省用价格分），自动排名
 */
@Service
@RequiredArgsConstructor
public class ProcEvaluationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EvaluationMapper evaluationMapper;
    private final EvalDetailMapper evalDetailMapper;
    private final BidMapper bidMapper;
    private final TenderMapper tenderMapper;
    private final ProjectMapper projectMapper;
    private final ParamService paramService;
    private final ProcBidService bidService;

    /** 专家评分请求体 */
    @Data
    public static class ScoreRequest {
        private Long bidId;
        private String expertName;
        private BigDecimal techScore;
        private BigDecimal bizScore;
    }

    public List<ProcEvaluation> list(Long procProjectId) {
        return evaluationMapper.selectList(new LambdaQueryWrapper<ProcEvaluation>()
                .eq(ProcEvaluation::getProcProjectId, procProjectId)
                .orderByDesc(ProcEvaluation::getId));
    }

    public ProcEvaluation detail(Long id) {
        ProcEvaluation e = evaluationMapper.selectById(id);
        if (e == null) throw new BizException("评标记录不存在");
        return e;
    }

    public List<ProcEvalDetail> details(Long evaluationId) {
        detail(evaluationId);
        return evalDetailMapper.selectList(new LambdaQueryWrapper<ProcEvalDetail>()
                .eq(ProcEvalDetail::getEvaluationId, evaluationId)
                .orderByAsc(ProcEvalDetail::getId));
    }

    /** 创建评标：前提发标已开标且报价家数达标（proc.award.min_bid_count，默认3家） */
    @Transactional
    public Long create(ProcEvaluation evaluation) {
        if (evaluation.getProcProjectId() == null) throw new BizException("缺少招采立项ID");
        ProcProject project = projectMapper.selectById(evaluation.getProcProjectId());
        if (project == null) throw new BizException("招采立项不存在");
        Long cnt = evaluationMapper.selectCount(new LambdaQueryWrapper<ProcEvaluation>()
                .eq(ProcEvaluation::getProcProjectId, evaluation.getProcProjectId()));
        if (cnt != null && cnt > 0) throw new BizException("该立项已存在评标记录");
        ProcTender tender = latestTender(evaluation.getProcProjectId());
        if (tender == null) throw new BizException("该立项尚未发标，无法评标");
        if (!"OPENED".equals(tender.getStatus())) throw new BizException("发标尚未开标，无法评标");
        long bidCount = bidMapper.selectCount(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getTenderId, tender.getId()));
        int minCount = paramService.getInt("proc.award.min_bid_count", 3);
        if (bidCount < minCount) throw new BizException("报价家数不足" + minCount + "家，无法评标");
        evaluation.setId(null);
        if (evaluation.getTechWeight() == null) evaluation.setTechWeight(new BigDecimal("60"));
        if (evaluation.getBizWeight() == null) evaluation.setBizWeight(new BigDecimal("40"));
        evaluation.setStatus("SCORING");
        evaluationMapper.insert(evaluation);
        project.setStatus("EVALUATING");
        if (projectMapper.updateById(project) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return evaluation.getId();
    }

    /** 专家评分：写 eval_detail 留痕；回标技术分取专家均值 */
    @Transactional
    public void score(Long id, ScoreRequest req) {
        ProcEvaluation evaluation = detail(id);
        if (!"SCORING".equals(evaluation.getStatus())) throw new BizException("评标已结束，不能再评分");
        if (req.getBidId() == null) throw new BizException("缺少回标记录ID");
        if (req.getExpertName() == null || req.getExpertName().isBlank()) throw new BizException("请填写专家姓名");
        ProcBid bid = bidMapper.selectById(req.getBidId());
        if (bid == null) throw new BizException("回标记录不存在");
        if (!bid.getProcProjectId().equals(evaluation.getProcProjectId())) {
            throw new BizException("回标记录不属于该评标的立项");
        }
        ProcEvalDetail d = new ProcEvalDetail();
        d.setEvaluationId(id);
        d.setBidId(bid.getId());
        d.setSupplierName(bid.getSupplierName());
        d.setExpertName(req.getExpertName());
        d.setTechScore(req.getTechScore());
        d.setBizScore(req.getBizScore());
        evalDetailMapper.insert(d);
        // 技术分：若该回标已有 bid_item 明细，按明细加权汇总；否则回退专家技术评分均值（兼容无明细旧数据）
        if (!bidService.recomputeIfHasItems(bid.getId())) {
            List<ProcEvalDetail> ds = evalDetailMapper.selectList(new LambdaQueryWrapper<ProcEvalDetail>()
                    .eq(ProcEvalDetail::getEvaluationId, id)
                    .eq(ProcEvalDetail::getBidId, bid.getId()));
            List<BigDecimal> techs = ds.stream().map(ProcEvalDetail::getTechScore)
                    .filter(Objects::nonNull).toList();
            if (!techs.isEmpty()) {
                BigDecimal avg = techs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(techs.size()), 2, RoundingMode.HALF_UP);
                bid.setTechScore(avg);
                if (bidMapper.updateById(bid) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
            }
        }
    }

    /**
     * 评标完成：总分=技术分×技术权重/100+商务分×商务权重/100（商务分缺省用价格分=最低价/该家价×100），
     * 自动生成排名，明细回写总分与排名，结论汇总前二名。
     */
    @Transactional
    public void finish(Long id) {
        ProcEvaluation evaluation = detail(id);
        if (!"SCORING".equals(evaluation.getStatus())) throw new BizException("评标已结束");
        ProcTender tender = latestTender(evaluation.getProcProjectId());
        if (tender == null) throw new BizException("该立项尚未发标");
        List<ProcBid> bids = bidMapper.selectList(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getTenderId, tender.getId())
                .ne(ProcBid::getBidStatus, "INVALID"));
        if (bids.isEmpty()) throw new BizException("暂无有效回标记录，无法完成评标");
        List<ProcEvalDetail> allDetails = evalDetailMapper.selectList(new LambdaQueryWrapper<ProcEvalDetail>()
                .eq(ProcEvalDetail::getEvaluationId, id));
        Map<Long, List<ProcEvalDetail>> detailsByBid = allDetails.stream()
                .collect(Collectors.groupingBy(ProcEvalDetail::getBidId));
        BigDecimal lowestPrice = bids.stream().map(ProcBid::getPriceAmount).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);

        // 每家总分（技术分=专家均值已回写回标；商务分=专家商务均分，缺省价格分）
        Map<Long, BigDecimal> totalByBid = new HashMap<>();
        for (ProcBid bid : bids) {
            BigDecimal tech = bid.getTechScore() == null ? BigDecimal.ZERO : bid.getTechScore();
            BigDecimal biz = bizScoreOf(bid, detailsByBid.get(bid.getId()), lowestPrice);
            totalByBid.put(bid.getId(), tech.multiply(evaluation.getTechWeight())
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                    .add(biz.multiply(evaluation.getBizWeight())
                            .divide(HUNDRED, 4, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        List<ProcBid> ranked = new ArrayList<>(bids);
        ranked.sort(Comparator.comparing((ProcBid b) -> totalByBid.get(b.getId())).reversed());

        // 明细回写：总分（该专家口径，商务缺省价格分）+ 排名（该回标排名）
        for (int i = 0; i < ranked.size(); i++) {
            ProcBid bid = ranked.get(i);
            int rankNo = i + 1;
            List<ProcEvalDetail> ds = detailsByBid.getOrDefault(bid.getId(), List.of());
            for (ProcEvalDetail d : ds) {
                d.setTotalScore(detailTotal(d, evaluation, priceScoreOf(bid, lowestPrice)));
                d.setRankNo(rankNo);
                if (evalDetailMapper.updateById(d) == 0) {
                    throw new BizException("数据已被他人修改，请刷新后重试");
                }
            }
        }

        // 结论：汇总前二名
        StringBuilder sb = new StringBuilder("评标完成，推荐排序：第一名 ")
                .append(ranked.get(0).getSupplierName())
                .append("（").append(totalByBid.get(ranked.get(0).getId())).append("分）");
        if (ranked.size() > 1) {
            sb.append("；第二名 ").append(ranked.get(1).getSupplierName())
                    .append("（").append(totalByBid.get(ranked.get(1).getId())).append("分）");
        }
        evaluation.setStatus("REPORTED");
        evaluation.setConclusion(sb.toString());
        if (evaluationMapper.updateById(evaluation) == 0) {
            throw new BizException("数据已被他人修改，请刷新后重试");
        }
    }

    /** 商务分：专家商务均分，缺省用价格分（最低价/该家价×100） */
    private BigDecimal bizScoreOf(ProcBid bid, List<ProcEvalDetail> details, BigDecimal lowestPrice) {
        if (details != null) {
            List<BigDecimal> bizList = details.stream().map(ProcEvalDetail::getBizScore)
                    .filter(Objects::nonNull).toList();
            if (!bizList.isEmpty()) {
                return bizList.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(bizList.size()), 2, RoundingMode.HALF_UP);
            }
        }
        return priceScoreOf(bid, lowestPrice);
    }

    private BigDecimal priceScoreOf(ProcBid bid, BigDecimal lowestPrice) {
        if (lowestPrice == null || bid.getPriceAmount() == null
                || bid.getPriceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return lowestPrice.divide(bid.getPriceAmount(), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal detailTotal(ProcEvalDetail d, ProcEvaluation evaluation, BigDecimal priceScore) {
        BigDecimal tech = d.getTechScore() == null ? BigDecimal.ZERO : d.getTechScore();
        BigDecimal biz = d.getBizScore() == null ? priceScore : d.getBizScore();
        return tech.multiply(evaluation.getTechWeight()).divide(HUNDRED, 4, RoundingMode.HALF_UP)
                .add(biz.multiply(evaluation.getBizWeight()).divide(HUNDRED, 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ProcTender latestTender(Long procProjectId) {
        List<ProcTender> ts = tenderMapper.selectList(new LambdaQueryWrapper<ProcTender>()
                .eq(ProcTender::getProcProjectId, procProjectId)
                .orderByDesc(ProcTender::getId));
        return ts.isEmpty() ? null : ts.get(0);
    }
}
