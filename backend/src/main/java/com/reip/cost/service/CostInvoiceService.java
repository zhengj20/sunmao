package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostInvoice;
import com.reip.cost.mapper.CostMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 收票登记（发票池）：发票号全平台唯一查重
 */
@Service
@RequiredArgsConstructor
public class CostInvoiceService {

    private final CostMappers.InvoiceMapper invoiceMapper;

    public PageResult<CostInvoice> page(long current, long size, Long projectId, Long contractId,
                                        String checkStatus, String kw) {
        LambdaQueryWrapper<CostInvoice> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostInvoice::getProjectId, projectId);
        if (contractId != null) qw.eq(CostInvoice::getContractId, contractId);
        if (checkStatus != null && !checkStatus.isBlank()) qw.eq(CostInvoice::getCheckStatus, checkStatus);
        if (kw != null && !kw.isBlank()) qw.like(CostInvoice::getInvoiceNo, kw);
        qw.orderByDesc(CostInvoice::getId);
        Page<CostInvoice> p = invoiceMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostInvoice detail(Long id) {
        CostInvoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BizException("发票不存在");
        return invoice;
    }

    @Transactional
    public Long create(CostInvoice invoice) {
        if (invoice == null) throw new BizException("缺少表单内容");
        if (invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) throw new BizException("请填写发票号");
        if (invoice.getAmount() == null) throw new BizException("请填写发票金额");
        checkDuplicate(invoice.getInvoiceNo(), null);
        invoice.setId(null);
        invoice.setDuplicateHash(sha256Hex(invoice.getInvoiceNo()));
        if (invoice.getCheckStatus() == null || invoice.getCheckStatus().isBlank())
            invoice.setCheckStatus("UNCHECKED");
        invoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Transactional
    public void update(CostInvoice invoice) {
        if (invoice == null || invoice.getId() == null) throw new BizException("缺少发票ID");
        CostInvoice db = invoiceMapper.selectById(invoice.getId());
        if (db == null) throw new BizException("发票不存在");
        if (invoice.getInvoiceNo() != null && !invoice.getInvoiceNo().isBlank()
                && !invoice.getInvoiceNo().equals(db.getInvoiceNo())) {
            checkDuplicate(invoice.getInvoiceNo(), invoice.getId());
            invoice.setDuplicateHash(sha256Hex(invoice.getInvoiceNo()));
        } else {
            invoice.setInvoiceNo(null);
        }
        if (invoiceMapper.updateById(invoice) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        if (invoiceMapper.selectById(id) == null) throw new BizException("发票不存在");
        invoiceMapper.deleteById(id);
    }

    /** 发票号全平台查重（排除自身） */
    private void checkDuplicate(String invoiceNo, Long excludeId) {
        Long cnt = invoiceMapper.selectCount(new LambdaQueryWrapper<CostInvoice>()
                .eq(CostInvoice::getInvoiceNo, invoiceNo)
                .ne(excludeId != null, CostInvoice::getId, excludeId));
        if (cnt != null && cnt > 0) throw new BizException("发票号已存在（全平台查重）");
    }

    /** duplicate_hash = 发票号的 SHA-256 */
    private String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(text.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text; // 理论不可达
        }
    }
}
