package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmDistributeLog;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 主数据分发日志：供应商/项目等主数据变更时留痕，供下游域感知
 */
@Service
@RequiredArgsConstructor
public class MdmDistributeLogService {

    private final MdmMappers.DistributeLogMapper distributeLogMapper;

    /** 记录一条分发日志（在外层事务内则随事务一起提交） */
    public void record(String dataType, Long dataId) {
        MdmDistributeLog log = new MdmDistributeLog();
        log.setDataType(dataType);
        log.setDataId(dataId);
        log.setTargetDomain("ALL");
        log.setStatus("SUCCESS");
        log.setCreatedAt(LocalDateTime.now());
        distributeLogMapper.insert(log);
    }

    /** 分发日志分页 */
    public PageResult<MdmDistributeLog> page(long current, long size, String dataType) {
        LambdaQueryWrapper<MdmDistributeLog> qw = new LambdaQueryWrapper<>();
        if (dataType != null && !dataType.isBlank()) qw.eq(MdmDistributeLog::getDataType, dataType);
        qw.orderByDesc(MdmDistributeLog::getId);
        Page<MdmDistributeLog> p = distributeLogMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }
}
