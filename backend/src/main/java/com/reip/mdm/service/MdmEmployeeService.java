package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmEmployee;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 员工档案服务
 */
@Service
@RequiredArgsConstructor
public class MdmEmployeeService {

    private final MdmMappers.EmployeeMapper employeeMapper;

    /** 分页（关联 user_id 展示登录账号姓名、组织名称） */
    public PageResult<MdmEmployee> page(long current, long size, String kw, String status, Long orgId) {
        IPage<MdmEmployee> p = employeeMapper.selectEmployeePage(new Page<>(current, size), kw, status, orgId);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public MdmEmployee detail(Long id) {
        MdmEmployee e = employeeMapper.selectById(id);
        if (e == null) throw new BizException("员工不存在");
        return e;
    }

    @Transactional
    public Long create(MdmEmployee emp) {
        if (emp.getRealName() == null || emp.getRealName().isBlank()) throw new BizException("员工姓名不能为空");
        if (emp.getStatus() == null || emp.getStatus().isBlank()) emp.setStatus("IN_SERVICE");
        emp.setId(null);
        employeeMapper.insert(emp);
        return emp.getId();
    }

    @Transactional
    public void update(MdmEmployee emp) {
        if (emp.getId() == null) throw new BizException("缺少员工ID");
        if (emp.getRealName() == null || emp.getRealName().isBlank()) throw new BizException("员工姓名不能为空");
        int updated = employeeMapper.updateById(emp);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        employeeMapper.deleteById(id);
    }
}
