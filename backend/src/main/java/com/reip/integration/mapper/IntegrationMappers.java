package com.reip.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.integration.entity.IntAdapterConfig;
import com.reip.integration.entity.IntApiRegistry;
import com.reip.integration.entity.IntAppKey;
import com.reip.integration.entity.IntCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface IntegrationMappers {

    @Mapper
    interface ApiRegistryMapper extends BaseMapper<IntApiRegistry> {
    }

    @Mapper
    interface AdapterMapper extends BaseMapper<IntAdapterConfig> {
    }

    @Mapper
    interface AppKeyMapper extends BaseMapper<IntAppKey> {
    }

    @Mapper
    interface CallLogMapper extends BaseMapper<IntCallLog> {

        /** 按适配器统计：成功数/失败数/平均耗时 */
        @Select("""
            SELECT adapter_code AS adapterCode,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount,
                   ROUND(AVG(cost_ms)) AS avgCost
            FROM int_call_log
            GROUP BY adapter_code
            ORDER BY adapter_code
            """)
        List<Map<String, Object>> selectStats();
    }
}
