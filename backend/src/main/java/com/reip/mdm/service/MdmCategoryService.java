package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.mdm.entity.MdmCategory;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分类树服务：合同分类/采购品类/变更分类/费用科目
 */
@Service
@RequiredArgsConstructor
public class MdmCategoryService {

    private final MdmMappers.CategoryMapper categoryMapper;

    /** 支持的分类类型 */
    private static final Set<String> TYPES = Set.of("CONTRACT", "PURCHASE", "CHANGE", "EXPENSE");

    /** 分类树（按类型过滤） */
    public List<MdmCategory> tree(String type) {
        LambdaQueryWrapper<MdmCategory> qw = new LambdaQueryWrapper<MdmCategory>()
                .orderByAsc(MdmCategory::getSort).orderByAsc(MdmCategory::getId);
        if (type != null && !type.isBlank()) qw.eq(MdmCategory::getCategoryType, type);
        List<MdmCategory> all = categoryMapper.selectList(qw);
        Map<Long, List<MdmCategory>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(MdmCategory::getParentId));
        List<MdmCategory> roots = byParent.getOrDefault(0L, List.of());
        roots.forEach(r -> attach(r, byParent));
        return roots;
    }

    private void attach(MdmCategory node, Map<Long, List<MdmCategory>> byParent) {
        List<MdmCategory> children = byParent.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            children.forEach(c -> attach(c, byParent));
        }
    }

    @Transactional
    public Long create(MdmCategory category) {
        if (category.getCategoryType() == null || !TYPES.contains(category.getCategoryType()))
            throw new BizException("分类类型不合法，应为 CONTRACT/PURCHASE/CHANGE/EXPENSE");
        if (category.getCode() == null || category.getCode().isBlank()) throw new BizException("分类编码不能为空");
        if (category.getName() == null || category.getName().isBlank()) throw new BizException("分类名称不能为空");
        if (category.getParentId() == null) category.setParentId(0L);
        if (category.getParentId() != 0 && categoryMapper.selectById(category.getParentId()) == null)
            throw new BizException("上级分类不存在");
        category.setId(null);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional
    public void update(MdmCategory category) {
        if (category.getId() == null) throw new BizException("缺少分类ID");
        category.setCategoryType(null); // 分类类型不可修改
        int updated = categoryMapper.updateById(category);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 删除校验：有子节点禁止删除 */
    @Transactional
    public void delete(Long id) {
        Long childCnt = categoryMapper.selectCount(new LambdaQueryWrapper<MdmCategory>()
                .eq(MdmCategory::getParentId, id));
        if (childCnt > 0) throw new BizException("存在下级分类，无法删除");
        categoryMapper.deleteById(id);
    }
}
