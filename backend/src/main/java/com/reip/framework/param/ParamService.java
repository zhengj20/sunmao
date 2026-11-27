package com.reip.framework.param;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 系统参数读取（sys_param，业务规则阈值统一来源）
 */
@Service
public class ParamService {

    @Mapper
    public interface ParamMapper {
        @Select("SELECT param_value FROM sys_param WHERE param_key = #{key} AND deleted = 0")
        String selectValue(String key);
    }

    private final ParamMapper mapper;

    public ParamService(ParamMapper mapper) {
        this.mapper = mapper;
    }

    public String get(String key) {
        return mapper.selectValue(key);
    }

    public String get(String key, String defaultValue) {
        String v = mapper.selectValue(key);
        return v == null ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(mapper.selectValue(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try {
            return new BigDecimal(mapper.selectValue(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = mapper.selectValue(key);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }
}
