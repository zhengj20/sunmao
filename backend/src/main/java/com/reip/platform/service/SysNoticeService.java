package com.reip.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.framework.auth.LoginUser;
import com.reip.framework.auth.UserContext;
import com.reip.platform.entity.SysNotice;
import com.reip.platform.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统公告：分页 / 增删改 / 置顶 / 门户取数
 */
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeMapper noticeMapper;

    public PageResult<SysNotice> page(long current, long size, String status, String noticeType) {
        LambdaQueryWrapper<SysNotice> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(SysNotice::getStatus, status);
        if (noticeType != null && !noticeType.isBlank()) qw.eq(SysNotice::getNoticeType, noticeType);
        qw.orderByDesc(SysNotice::getPinned).orderByDesc(SysNotice::getId);
        Page<SysNotice> p = noticeMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SysNotice detail(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) throw new BizException("公告不存在");
        return notice;
    }

    @Transactional
    public Long create(SysNotice notice) {
        notice.setId(null);
        if (notice.getStatus() == null || notice.getStatus().isBlank()) notice.setStatus("PUBLISHED");
        if (notice.getNoticeType() == null || notice.getNoticeType().isBlank()) notice.setNoticeType("NOTICE");
        if (notice.getPinned() == null) notice.setPinned(0);
        LoginUser user = UserContext.get();
        if (user != null) {
            if (notice.getPublisherId() == null) notice.setPublisherId(user.getUserId());
            if (notice.getPublisherName() == null || notice.getPublisherName().isBlank()) {
                notice.setPublisherName(user.getRealName());
            }
        }
        if (notice.getPublishAt() == null) notice.setPublishAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        return notice.getId();
    }

    @Transactional
    public void update(SysNotice notice) {
        SysNotice exist = detail(notice.getId());
        notice.setVersion(exist.getVersion());
        int updated = noticeMapper.updateById(notice);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        noticeMapper.deleteById(id);
    }

    @Transactional
    public void togglePin(Long id, boolean pinned) {
        SysNotice exist = detail(id);
        SysNotice up = new SysNotice();
        up.setId(id);
        up.setVersion(exist.getVersion());
        up.setPinned(pinned ? 1 : 0);
        int updated = noticeMapper.updateById(up);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 门户公告：置顶优先 + 已发布，取最近 limit 条 */
    public List<SysNotice> listForPortal(int limit) {
        LambdaQueryWrapper<SysNotice> qw = new LambdaQueryWrapper<>();
        qw.eq(SysNotice::getStatus, "PUBLISHED");
        qw.orderByDesc(SysNotice::getPinned).orderByDesc(SysNotice::getId);
        Page<SysNotice> p = noticeMapper.selectPage(new Page<>(1, limit, false), qw);
        return p.getRecords();
    }
}
