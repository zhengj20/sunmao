package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpInvoice;
import com.reip.expense.mapper.ExpenseMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发票池（全集团进项）：R2 全平台查重 / 验真状态管理 / OCR 识别（AI 占位）
 */
@Service
@RequiredArgsConstructor
public class ExpInvoiceService {

    private final ExpenseMappers.InvoiceMapper invoiceMapper;

    public PageResult<ExpInvoice> page(long current, long size, String supplier, String source,
                                       String verifyStatus, String kw) {
        LambdaQueryWrapper<ExpInvoice> qw = new LambdaQueryWrapper<>();
        if (supplier != null && !supplier.isBlank()) qw.like(ExpInvoice::getSupplier, supplier);
        if (source != null && !source.isBlank()) qw.eq(ExpInvoice::getSource, source);
        if (verifyStatus != null && !verifyStatus.isBlank()) qw.eq(ExpInvoice::getVerifyStatus, verifyStatus);
        if (kw != null && !kw.isBlank()) qw.like(ExpInvoice::getInvNo, kw);
        qw.orderByDesc(ExpInvoice::getId);
        Page<ExpInvoice> p = invoiceMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpInvoice detail(Long id) {
        ExpInvoice i = invoiceMapper.selectById(id);
        if (i == null) throw new BizException("发票不存在");
        return i;
    }

    /** 登记发票：R2 全平台查重（duplicate_hash 唯一 + 成本池发票号交叉） */
    @Transactional
    public Long create(ExpInvoice i) {
        if (i == null) throw new BizException("缺少发票内容");
        if (i.getInvNo() == null || i.getInvNo().isBlank()) throw new BizException("请填写发票号");
        if (i.getTaxNo() == null || i.getTaxNo().isBlank()) throw new BizException("请填写发票代码/税号");
        ExpSupport.requireAmount(i.getAmount(), "发票金额");
        String hash = ExpSupport.invoiceHash(i.getTaxNo(), i.getInvNo());
        // R2 全平台查重
        long dup = invoiceMapper.countDuplicate(hash, i.getInvNo().trim(), null);
        if (dup > 0) throw new BizException("发票重复：该发票已存在于平台发票池（R2 查重阻断）");
        i.setId(null);
        i.setDuplicateHash(hash);
        if (i.getVerifyStatus() == null || i.getVerifyStatus().isBlank()) i.setVerifyStatus("UNVERIFIED");
        if (i.getSource() == null || i.getSource().isBlank()) i.setSource("REIMBURSE");
        if (i.getStatus() == null || i.getStatus().isBlank()) i.setStatus("ACTIVE");
        invoiceMapper.insert(i);
        return i.getId();
    }

    @Transactional
    public void update(ExpInvoice i) {
        if (i == null || i.getId() == null) throw new BizException("缺少发票ID");
        ExpInvoice db = invoiceMapper.selectById(i.getId());
        if (db == null) throw new BizException("发票不存在");
        boolean keyChanged = (i.getInvNo() != null && !i.getInvNo().equals(db.getInvNo()))
                || (i.getTaxNo() != null && !i.getTaxNo().equals(db.getTaxNo()));
        if (keyChanged) {
            String hash = ExpSupport.invoiceHash(
                    i.getTaxNo() == null ? db.getTaxNo() : i.getTaxNo(),
                    i.getInvNo() == null ? db.getInvNo() : i.getInvNo());
            long dup = invoiceMapper.countDuplicate(hash,
                    i.getInvNo() == null ? db.getInvNo() : i.getInvNo(), i.getId());
            if (dup > 0) throw new BizException("发票重复：该发票已存在于平台发票池（R2 查重阻断）");
            i.setDuplicateHash(hash);
        }
        if (invoiceMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        if (invoiceMapper.selectById(id) == null) throw new BizException("发票不存在");
        invoiceMapper.deleteById(id);
    }

    /**
     * 发票验真（占位实现，待接 ai-service）。
     * 当前基于税号规则的简单校验：税号长度 ≥ 15 视为有效 → PASS，否则 FAIL。
     * TODO: 接入 ai-service / 税务验真接口，返回真实真伪结论。
     */
    @Transactional
    public ExpInvoice verifyInvoice(Long id) {
        ExpInvoice i = invoiceMapper.selectById(id);
        if (i == null) throw new BizException("发票不存在");
        String taxNo = i.getTaxNo() == null ? "" : i.getTaxNo().trim();
        boolean valid = taxNo.length() >= 15; // 占位规则：真实验真需调外部服务
        i.setVerifyStatus(valid ? "PASS" : "FAIL");
        if (invoiceMapper.updateById(i) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        return i;
    }

    /**
     * OCR 识别（占位实现，待接 ai-service）。
     * 当前仅返回传入字段回显，不做真实识别。
     * TODO: 接入 ai-service OCR，识别发票号/税号/金额/税额并回写。
     */
    public Map<String, Object> ocrRecognize(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recognized", false);
        result.put("message", "OCR 识别为占位实现，待接 ai-service");
        if (input != null) {
            result.put("invNo", input.get("invNo"));
            result.put("taxNo", input.get("taxNo"));
            result.put("amount", input.get("amount"));
        }
        return result;
    }
}
