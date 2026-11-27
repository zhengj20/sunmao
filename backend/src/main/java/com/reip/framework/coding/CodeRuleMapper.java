package com.reip.framework.coding;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CodeRuleMapper {

    @Select("SELECT pattern FROM mdm_code_rule WHERE rule_code = #{ruleCode} AND deleted = 0")
    String findPattern(String ruleCode);

    @Insert("INSERT IGNORE INTO mdm_code_counter(rule_code, dim_key, current_no) VALUES(#{ruleCode}, #{dimKey}, 0)")
    void ensureCounter(String ruleCode, String dimKey);

    @Select("SELECT current_no FROM mdm_code_counter WHERE rule_code = #{ruleCode} AND dim_key = #{dimKey} FOR UPDATE")
    Long lockCurrentNo(String ruleCode, String dimKey);

    @Update("UPDATE mdm_code_counter SET current_no = #{no} WHERE rule_code = #{ruleCode} AND dim_key = #{dimKey}")
    void updateNo(String ruleCode, String dimKey, Long no);
}
