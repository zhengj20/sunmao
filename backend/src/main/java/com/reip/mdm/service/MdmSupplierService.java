package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.framework.event.EventPublisher;
import com.reip.mdm.entity.MdmSupplier;
import com.reip.mdm.entity.MdmSupplierQualification;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 供应商服务：档案 CRUD、资质证照、拉黑/恢复、到期提醒
 */
@Service
@RequiredArgsConstructor
public class MdmSupplierService {

    private final MdmMappers.SupplierMapper supplierMapper;
    private final MdmMappers.SupplierQualificationMapper qualificationMapper;
    private final CodeRuleService codeRuleService;
    private final EventPublisher eventPublisher;
    private final MdmDistributeLogService distributeLogService;

    public PageResult<MdmSupplier> page(long current, long size, String kw, String libType) {
        LambdaQueryWrapper<MdmSupplier> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(MdmSupplier::getName, kw)
                .or().like(MdmSupplier::getShortName, kw)
                .or().like(MdmSupplier::getCreditCode, kw)
                .or().like(MdmSupplier::getSupplierCode, kw));
        if (libType != null && !libType.isBlank()) qw.eq(MdmSupplier::getLibType, libType);
        qw.orderByDesc(MdmSupplier::getId);
        Page<MdmSupplier> p = supplierMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public MdmSupplier detail(Long id) {
        MdmSupplier s = supplierMapper.selectById(id);
        if (s == null) throw new BizException("供应商不存在");
        return s;
    }

    /** 供应商下拉选项：qualifiedOnly=true 时排除黑名单 */
    public List<MdmSupplier> options(boolean qualifiedOnly) {
        LambdaQueryWrapper<MdmSupplier> qw = new LambdaQueryWrapper<MdmSupplier>()
                .select(MdmSupplier::getId, MdmSupplier::getSupplierCode, MdmSupplier::getName, MdmSupplier::getLibType)
                .orderByAsc(MdmSupplier::getId);
        if (qualifiedOnly) qw.ne(MdmSupplier::getLibType, "BLACKLIST");
        return supplierMapper.selectList(qw);
    }

    /** 新增：supplier_code 用编码规则 GYS 自动生成；credit_code 唯一校验 */
    @Transactional
    public Long create(MdmSupplier supplier) {
        if (supplier.getName() == null || supplier.getName().isBlank()) throw new BizException("供应商名称不能为空");
        checkCreditCode(supplier.getCreditCode(), null);
        supplier.setId(null);
        supplier.setSupplierCode(codeRuleService.next("GYS", null));
        if (supplier.getLibType() == null || supplier.getLibType().isBlank()) supplier.setLibType("PRESELECT");
        supplierMapper.insert(supplier);
        return supplier.getId();
    }

    /** 统一社会信用代码唯一校验 */
    private void checkCreditCode(String creditCode, Long excludeId) {
        if (creditCode == null || creditCode.isBlank()) throw new BizException("统一社会信用代码不能为空");
        Long cnt = supplierMapper.selectCount(new LambdaQueryWrapper<MdmSupplier>()
                .eq(MdmSupplier::getCreditCode, creditCode)
                .ne(excludeId != null, MdmSupplier::getId, excludeId));
        if (cnt > 0) throw new BizException("该统一社会信用代码已存在档案，请勿重复建档");
    }

    @Transactional
    public void update(MdmSupplier supplier) {
        if (supplier.getId() == null) throw new BizException("缺少供应商ID");
        MdmSupplier old = supplierMapper.selectById(supplier.getId());
        if (old == null) throw new BizException("供应商不存在");
        checkCreditCode(supplier.getCreditCode(), supplier.getId());
        // 入黑名单必须走拉黑动作（保证事件发布）
        if ("BLACKLIST".equals(supplier.getLibType()) && !"BLACKLIST".equals(old.getLibType()))
            throw new BizException("请通过拉黑操作将供应商加入黑名单");
        supplier.setSupplierCode(null); // 编号不可修改
        supplier.setBlacklistReason(null); // 拉黑原因仅随拉黑/恢复动作维护
        int updated = supplierMapper.updateById(supplier);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        distributeLogService.record("supplier", supplier.getId());
    }

    /** 删除保护：被业务（合同）引用的供应商禁止删除 */
    @Transactional
    public void delete(Long id) {
        Long refCnt = supplierMapper.countContractRef(id);
        if (refCnt != null && refCnt > 0)
            throw new BizException("已被业务引用，禁止删除，可加入黑名单或停用");
        supplierMapper.deleteById(id);
    }

    /** 拉黑：置黑名单库并发布领域事件 */
    @Transactional
    public void blacklist(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("请填写拉黑原因");
        MdmSupplier old = supplierMapper.selectById(id);
        if (old == null) throw new BizException("供应商不存在");
        if ("BLACKLIST".equals(old.getLibType())) throw new BizException("该供应商已在黑名单中");
        MdmSupplier up = new MdmSupplier();
        up.setId(id);
        up.setVersion(old.getVersion());
        up.setLibType("BLACKLIST");
        up.setBlacklistReason(reason);
        int updated = supplierMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        distributeLogService.record("supplier", id);
        eventPublisher.publish("mdm.supplier.blacklisted", "MDM_SUPPLIER", id,
                Map.of("supplierId", id, "name", old.getName(), "reason", reason));
    }

    /** 恢复：回到预选库 */
    @Transactional
    public void restore(Long id) {
        MdmSupplier old = supplierMapper.selectById(id);
        if (old == null) throw new BizException("供应商不存在");
        MdmSupplier up = new MdmSupplier();
        up.setId(id);
        up.setVersion(old.getVersion());
        up.setLibType("PRESELECT");
        up.setBlacklistReason(""); // 清空拉黑原因
        int updated = supplierMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
        distributeLogService.record("supplier", id);
    }

    // ================= 资质证照 =================

    public List<MdmSupplierQualification> qualificationList(Long supplierId) {
        requireSupplier(supplierId);
        return qualificationMapper.selectList(new LambdaQueryWrapper<MdmSupplierQualification>()
                .eq(MdmSupplierQualification::getSupplierId, supplierId)
                .orderByDesc(MdmSupplierQualification::getId));
    }

    @Transactional
    public Long createQualification(Long supplierId, MdmSupplierQualification qual) {
        requireSupplier(supplierId);
        if (qual.getQualType() == null || qual.getQualType().isBlank()) throw new BizException("请填写证照类型");
        qual.setId(null);
        qual.setSupplierId(supplierId);
        qualificationMapper.insert(qual);
        return qual.getId();
    }

    @Transactional
    public void updateQualification(Long supplierId, Long qualId, MdmSupplierQualification qual) {
        MdmSupplierQualification old = requireQualification(supplierId, qualId);
        qual.setId(qualId);
        qual.setSupplierId(null);
        qual.setVersion(old.getVersion());
        int updated = qualificationMapper.updateById(qual);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void deleteQualification(Long supplierId, Long qualId) {
        requireQualification(supplierId, qualId);
        qualificationMapper.deleteById(qualId);
    }

    /** 到期提醒：days 天内到期的资质证照 */
    public List<MdmSupplierQualification> expiring(int days) {
        if (days < 0) throw new BizException("天数不能为负数");
        return qualificationMapper.selectExpiring(days);
    }

    private void requireSupplier(Long supplierId) {
        if (supplierMapper.selectById(supplierId) == null) throw new BizException("供应商不存在");
    }

    private MdmSupplierQualification requireQualification(Long supplierId, Long qualId) {
        MdmSupplierQualification qual = qualificationMapper.selectById(qualId);
        if (qual == null || !qual.getSupplierId().equals(supplierId)) throw new BizException("资质证照不存在");
        return qual;
    }
}
