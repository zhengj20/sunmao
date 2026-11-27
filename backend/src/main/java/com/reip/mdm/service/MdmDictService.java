package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.mdm.entity.MdmDict;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 统一码值字典服务
 */
@Service
@RequiredArgsConstructor
public class MdmDictService {

    private final MdmMappers.DictMapper dictMapper;

    /** 按字典键取码值列表（前端下拉用） */
    public List<MdmDict> listByKey(String key) {
        if (key == null || key.isBlank()) throw new BizException("缺少字典key");
        return dictMapper.selectList(new LambdaQueryWrapper<MdmDict>()
                .eq(MdmDict::getDictKey, key)
                .orderByAsc(MdmDict::getSort).orderByAsc(MdmDict::getId));
    }

    /** 字典管理分页 */
    public PageResult<MdmDict> page(long current, long size, String kw) {
        LambdaQueryWrapper<MdmDict> qw = new LambdaQueryWrapper<>();
        if (kw != null && !kw.isBlank())
            qw.and(w -> w.like(MdmDict::getDictKey, kw).or().like(MdmDict::getLabel, kw).or().like(MdmDict::getValue, kw));
        qw.orderByAsc(MdmDict::getDictKey).orderByAsc(MdmDict::getSort).orderByAsc(MdmDict::getId);
        Page<MdmDict> p = dictMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Long create(MdmDict dict) {
        if (dict.getDictKey() == null || dict.getDictKey().isBlank()) throw new BizException("字典key不能为空");
        if (dict.getLabel() == null || dict.getLabel().isBlank()) throw new BizException("显示名不能为空");
        if (dict.getValue() == null || dict.getValue().isBlank()) throw new BizException("码值不能为空");
        dict.setId(null);
        dictMapper.insert(dict);
        return dict.getId();
    }

    @Transactional
    public void update(MdmDict dict) {
        if (dict.getId() == null) throw new BizException("缺少字典ID");
        int updated = dictMapper.updateById(dict);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        dictMapper.deleteById(id);
    }
}
