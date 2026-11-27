package com.reip.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.LoginUser;
import com.reip.framework.auth.UserContext;
import com.reip.workflow.entity.WflEntrust;
import com.reip.workflow.mapper.WorkflowMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 委托代理管理
 */
@Service
@RequiredArgsConstructor
public class WflEntrustService {

    private final WorkflowMappers.EntrustMapper entrustMapper;

    /** 分页列表（全部，管理视角） */
    public PageResult<WflEntrust> page(long current, long size, String status) {
        LambdaQueryWrapper<WflEntrust> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(WflEntrust::getStatus, status);
        qw.orderByDesc(WflEntrust::getId);
        Page<WflEntrust> p = entrustMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 我的有效委托（ACTIVE 且未到期） */
    public List<WflEntrust> mine() {
        Long me = UserContext.require().getUserId();
        return entrustMapper.selectList(new LambdaQueryWrapper<WflEntrust>()
                .eq(WflEntrust::getFromUserId, me)
                .eq(WflEntrust::getStatus, "ACTIVE")
                .ge(WflEntrust::getEndDate, LocalDate.now())
                .orderByAsc(WflEntrust::getEndDate));
    }

    /** 创建委托：from_user 为当前人 */
    @Transactional
    public Long create(WflEntrust entrust) {
        LoginUser me = UserContext.require();
        if (entrust.getToUserId() == null || entrust.getToUserName() == null || entrust.getToUserName().isBlank()) {
            throw new BizException("请选择受托人");
        }
        if (entrust.getToUserId().equals(me.getUserId())) throw new BizException("不能委托给自己");
        if (entrust.getStartDate() == null || entrust.getEndDate() == null) throw new BizException("请指定委托起止日期");
        if (entrust.getEndDate().isBefore(entrust.getStartDate())) throw new BizException("结束日期不能早于开始日期");
        entrust.setId(null);
        entrust.setFromUserId(me.getUserId());
        entrust.setFromUserName(me.getRealName());
        if (entrust.getBizScope() == null || entrust.getBizScope().isBlank()) entrust.setBizScope("ALL");
        entrust.setStatus("ACTIVE");
        entrustMapper.insert(entrust);
        return entrust.getId();
    }

    /** 结束委托：仅委托人本人 */
    @Transactional
    public void close(Long id) {
        WflEntrust entrust = entrustMapper.selectById(id);
        if (entrust == null) throw new BizException("委托不存在");
        LoginUser me = UserContext.require();
        if (!me.getUserId().equals(entrust.getFromUserId())) throw new BizException("仅委托人本人可结束委托");
        WflEntrust up = new WflEntrust();
        up.setId(id);
        up.setVersion(entrust.getVersion());
        up.setStatus("CLOSED");
        if (entrustMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }
}
