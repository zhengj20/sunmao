package com.reip.proc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.coding.CodeRuleService;
import com.reip.proc.entity.ProcFramework;
import com.reip.proc.entity.ProcFrameworkOrder;
import com.reip.proc.mapper.ProcMappers.FrameworkMapper;
import com.reip.proc.mapper.ProcMappers.FrameworkOrderMapper;
import com.reip.proc.mapper.ProcMappers.MdmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 战略集采：协议 CRUD（框架单价表 JSON）+ 下单 + 执行量统计
 */
@Service
@RequiredArgsConstructor
public class ProcFrameworkService {

    private final FrameworkMapper frameworkMapper;
    private final FrameworkOrderMapper orderMapper;
    private final MdmMapper mdmMapper;
    private final CodeRuleService codeRuleService;

    public PageResult<ProcFramework> page(long current, long size, String kw, String status) {
        LambdaQueryWrapper<ProcFramework> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(ProcFramework::getFrameworkCode, kw)
                .or().like(ProcFramework::getName, kw));
        if (status != null && !status.isBlank()) qw.eq(ProcFramework::getStatus, status);
        qw.orderByDesc(ProcFramework::getId);
        Page<ProcFramework> p = frameworkMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ProcFramework detail(Long id) {
        ProcFramework f = frameworkMapper.selectById(id);
        if (f == null) throw new BizException("战略集采协议不存在");
        return f;
    }

    @Transactional
    public Long create(ProcFramework framework) {
        if (framework.getSupplierId() != null) {
            Map<String, Object> supplier = mdmMapper.selectSupplier(framework.getSupplierId());
            if (supplier == null) throw new BizException("供应商不存在");
            framework.setSupplierName((String) supplier.get("name"));
        }
        framework.setId(null);
        framework.setFrameworkCode(codeRuleService.next("FW", null));
        if (framework.getStatus() == null || framework.getStatus().isBlank()) {
            framework.setStatus("ACTIVE");
        }
        frameworkMapper.insert(framework);
        return framework.getId();
    }

    @Transactional
    public void update(ProcFramework framework) {
        ProcFramework db = detail(framework.getId());
        if (framework.getSupplierId() != null) {
            Map<String, Object> supplier = mdmMapper.selectSupplier(framework.getSupplierId());
            if (supplier == null) throw new BizException("供应商不存在");
            db.setSupplierId(framework.getSupplierId());
            db.setSupplierName((String) supplier.get("name"));
        }
        if (framework.getName() != null) db.setName(framework.getName());
        if (framework.getCategoryId() != null) db.setCategoryId(framework.getCategoryId());
        // 框架单价表 JSON 由前端整体维护
        db.setUnitPriceTable(framework.getUnitPriceTable());
        db.setValidFrom(framework.getValidFrom());
        db.setValidTo(framework.getValidTo());
        if (framework.getStatus() != null) db.setStatus(framework.getStatus());
        db.setRemark(framework.getRemark());
        if (frameworkMapper.updateById(db) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        Long cnt = orderMapper.selectCount(new LambdaQueryWrapper<ProcFrameworkOrder>()
                .eq(ProcFrameworkOrder::getFrameworkId, id));
        if (cnt != null && cnt > 0) throw new BizException("该协议已存在下单记录，不能删除");
        frameworkMapper.deleteById(id);
    }

    /** 集采下单 */
    @Transactional
    public Long order(Long frameworkId, ProcFrameworkOrder order) {
        ProcFramework framework = detail(frameworkId);
        if (!"ACTIVE".equals(framework.getStatus())) throw new BizException("协议已失效，不能下单");
        if (order.getProjectId() == null) throw new BizException("缺少项目ID");
        order.setId(null);
        order.setFrameworkId(frameworkId);
        order.setOrderCode(codeRuleService.next("DD", null));
        order.setStatus("ORDERED");
        orderMapper.insert(order);
        return order.getId();
    }

    /** 执行量统计 */
    public Map<String, Object> usage(Long frameworkId) {
        detail(frameworkId);
        List<ProcFrameworkOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ProcFrameworkOrder>()
                .eq(ProcFrameworkOrder::getFrameworkId, frameworkId));
        BigDecimal totalQuantity = orders.stream().map(ProcFrameworkOrder::getQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = orders.stream().map(ProcFrameworkOrder::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("frameworkId", frameworkId);
        m.put("orderCount", orders.size());
        m.put("totalQuantity", totalQuantity);
        m.put("totalAmount", totalAmount);
        return m;
    }
}
