package com.reip.portal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.portal.entity.CockpitMetricDef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标字典（metric_def）CRUD Mapper
 */
@Mapper
public interface ReportMetricMapper extends BaseMapper<CockpitMetricDef> {
}
