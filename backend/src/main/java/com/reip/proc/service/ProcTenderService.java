package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.proc.entity.ProcBid;
import com.reip.proc.entity.ProcProject;
import com.reip.proc.entity.ProcTender;
import com.reip.proc.mapper.ProcMappers.BidMapper;
import com.reip.proc.mapper.ProcMappers.ProjectMapper;
import com.reip.proc.mapper.ProcMappers.TenderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发标管理：一个立项一个发标，开标/截止/答疑（乐观锁保护 JSON 追加）/保证金退还
 */
@Service
@RequiredArgsConstructor
public class ProcTenderService {

    private final TenderMapper tenderMapper;
    private final ProjectMapper projectMapper;
    private final BidMapper bidMapper;
    private final CodeRuleService codeRuleService;
    private final ObjectMapper objectMapper;

    public PageResult<ProcTender> page(long current, long size, Long procProjectId, String status) {
        LambdaQueryWrapper<ProcTender> qw = new LambdaQueryWrapper<>();
        if (procProjectId != null) qw.eq(ProcTender::getProcProjectId, procProjectId);
        if (status != null && !status.isBlank()) qw.eq(ProcTender::getStatus, status);
        qw.orderByDesc(ProcTender::getId);
        Page<ProcTender> p = tenderMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcTender detail(Long id) {
        ProcTender t = tenderMapper.selectById(id);
        if (t == null) throw new BizException("发标记录不存在");
        return t;
    }

    /** 发标（每个立项仅一个发标），发标时间自动取当前时间 */
    @Transactional
    public Long create(ProcTender tender) {
        if (tender.getProcProjectId() == null) throw new BizException("缺少招采立项ID");
        ProcProject project = projectMapper.selectById(tender.getProcProjectId());
        if (project == null) throw new BizException("招采立项不存在");
        if (!"APPROVED".equals(project.getStatus()) && !"ENTRY".equals(project.getStatus())) {
            throw new BizException("立项审批通过并完成入围后才能发标");
        }
        Long cnt = tenderMapper.selectCount(new LambdaQueryWrapper<ProcTender>()
                .eq(ProcTender::getProcProjectId, tender.getProcProjectId()));
        if (cnt != null && cnt > 0) throw new BizException("该立项已存在发标记录，一个立项仅允许一次发标");
        tender.setId(null);
        tender.setTenderCode(codeRuleService.next("FB", null));
        tender.setPublishTime(LocalDateTime.now());
        tender.setStatus("PUBLISHED");
        if (tender.getDepositRefunded() == null) tender.setDepositRefunded(0);
        tenderMapper.insert(tender);
        project.setStatus("TENDERING");
        if (projectMapper.updateById(project) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return tender.getId();
    }

    /** 开标：PUBLISHED/CLARIFY→OPENED（已过截止或手动开标），回标 SUBMITTED→OPENED */
    @Transactional
    public void open(Long id) {
        ProcTender tender = detail(id);
        if (!"PUBLISHED".equals(tender.getStatus()) && !"CLARIFY".equals(tender.getStatus())) {
            throw new BizException("当前状态不允许开标");
        }
        tender.setStatus("OPENED");
        if (tenderMapper.updateById(tender) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        List<ProcBid> bids = bidMapper.selectList(new LambdaQueryWrapper<ProcBid>()
                .eq(ProcBid::getTenderId, id).eq(ProcBid::getBidStatus, "SUBMITTED"));
        for (ProcBid bid : bids) {
            bid.setBidStatus("OPENED");
            if (bidMapper.updateById(bid) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        }
    }

    /** 截止关闭 */
    @Transactional
    public void close(Long id) {
        ProcTender tender = detail(id);
        if ("CLOSED".equals(tender.getStatus())) throw new BizException("发标已关闭");
        tender.setStatus("CLOSED");
        if (tenderMapper.updateById(tender) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 答疑：读旧 clarify_notes 数组-追加-写回（乐观锁防并发覆盖） */
    @Transactional
    public void clarify(Long id, String question, String answer) {
        if (question == null || question.isBlank()) throw new BizException("请填写答疑问题");
        if (answer == null || answer.isBlank()) throw new BizException("请填写答疑答复");
        ProcTender tender = detail(id);
        List<Map<String, String>> notes = new ArrayList<>();
        if (tender.getClarifyNotes() != null && !tender.getClarifyNotes().isBlank()) {
            try {
                notes = objectMapper.readValue(tender.getClarifyNotes(),
                        new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                throw new BizException("答疑纪要解析失败，请联系管理员");
            }
        }
        Map<String, String> note = new LinkedHashMap<>();
        note.put("question", question);
        note.put("answer", answer);
        note.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        notes.add(note);
        try {
            tender.setClarifyNotes(objectMapper.writeValueAsString(notes));
        } catch (Exception e) {
            throw new BizException("答疑纪要序列化失败，请联系管理员");
        }
        if ("PUBLISHED".equals(tender.getStatus())) tender.setStatus("CLARIFY");
        if (tenderMapper.updateById(tender) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 保证金退还 */
    @Transactional
    public void refundDeposit(Long id) {
        ProcTender tender = detail(id);
        if (tender.getDepositAmount() == null || tender.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该发标未设置保证金");
        }
        if (tender.getDepositRefunded() != null && tender.getDepositRefunded() == 1) {
            throw new BizException("保证金已退还");
        }
        tender.setDepositRefunded(1);
        if (tenderMapper.updateById(tender) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }
}
