package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.mdm.entity.MdmCustomer;
import com.reip.mdm.entity.MdmCustomerRelative;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户服务：档案 CRUD（证件唯一）、关联人子表
 */
@Service
@RequiredArgsConstructor
public class MdmCustomerService {

    private final MdmMappers.CustomerMapper customerMapper;
    private final MdmMappers.CustomerRelativeMapper relativeMapper;
    private final CodeRuleService codeRuleService;

    public PageResult<MdmCustomer> page(long current, long size, String kw, String customerType) {
        LambdaQueryWrapper<MdmCustomer> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(MdmCustomer::getName, kw)
                .or().like(MdmCustomer::getCertNo, kw)
                .or().like(MdmCustomer::getMobile, kw)
                .or().like(MdmCustomer::getCustomerCode, kw));
        if (customerType != null && !customerType.isBlank()) qw.eq(MdmCustomer::getCustomerType, customerType);
        qw.orderByDesc(MdmCustomer::getId);
        Page<MdmCustomer> p = customerMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public MdmCustomer detail(Long id) {
        MdmCustomer c = customerMapper.selectById(id);
        if (c == null) throw new BizException("客户不存在");
        return c;
    }

    /** 新增：customer_code 用编码规则 KH 自动生成；证件唯一校验 */
    @Transactional
    public Long create(MdmCustomer customer) {
        if (customer.getName() == null || customer.getName().isBlank()) throw new BizException("客户名称不能为空");
        checkCert(customer.getCertType(), customer.getCertNo(), null);
        customer.setId(null);
        customer.setCustomerCode(codeRuleService.next("KH", null));
        if (customer.getCertType() == null || customer.getCertType().isBlank()) customer.setCertType("ID_CARD");
        if (customer.getCustomerType() == null || customer.getCustomerType().isBlank()) customer.setCustomerType("PERSON");
        customerMapper.insert(customer);
        return customer.getId();
    }

    /** 证件唯一校验：cert_type + cert_no 组合重复即疑似重复建档 */
    private void checkCert(String certType, String certNo, Long excludeId) {
        if (certNo == null || certNo.isBlank()) throw new BizException("证件号不能为空");
        String type = (certType == null || certType.isBlank()) ? "ID_CARD" : certType;
        Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<MdmCustomer>()
                .eq(MdmCustomer::getCertType, type)
                .eq(MdmCustomer::getCertNo, certNo)
                .ne(excludeId != null, MdmCustomer::getId, excludeId));
        if (cnt > 0) throw new BizException("证件号已存在，可能重复建档");
    }

    @Transactional
    public void update(MdmCustomer customer) {
        if (customer.getId() == null) throw new BizException("缺少客户ID");
        checkCert(customer.getCertType(), customer.getCertNo(), customer.getId());
        customer.setCustomerCode(null); // 编号不可修改
        int updated = customerMapper.updateById(customer);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        customerMapper.deleteById(id);
    }

    // ================= 关联人 =================

    public List<MdmCustomerRelative> relativeList(Long customerId) {
        requireCustomer(customerId);
        return relativeMapper.selectList(new LambdaQueryWrapper<MdmCustomerRelative>()
                .eq(MdmCustomerRelative::getCustomerId, customerId)
                .orderByDesc(MdmCustomerRelative::getId));
    }

    @Transactional
    public Long createRelative(Long customerId, MdmCustomerRelative relative) {
        requireCustomer(customerId);
        if (relative.getName() == null || relative.getName().isBlank()) throw new BizException("关联人姓名不能为空");
        relative.setId(null);
        relative.setCustomerId(customerId);
        relativeMapper.insert(relative);
        return relative.getId();
    }

    @Transactional
    public void updateRelative(Long customerId, Long relativeId, MdmCustomerRelative relative) {
        MdmCustomerRelative old = requireRelative(customerId, relativeId);
        relative.setId(relativeId);
        relative.setCustomerId(null);
        relative.setVersion(old.getVersion());
        int updated = relativeMapper.updateById(relative);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteRelative(Long customerId, Long relativeId) {
        requireRelative(customerId, relativeId);
        relativeMapper.deleteById(relativeId);
    }

    private void requireCustomer(Long customerId) {
        if (customerMapper.selectById(customerId) == null) throw new BizException("客户不存在");
    }

    private MdmCustomerRelative requireRelative(Long customerId, Long relativeId) {
        MdmCustomerRelative r = relativeMapper.selectById(relativeId);
        if (r == null || !r.getCustomerId().equals(customerId)) throw new BizException("关联人不存在");
        return r;
    }
}
