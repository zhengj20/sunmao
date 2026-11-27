package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.mdm.entity.MdmOrg;
import com.reip.mdm.entity.MdmProject;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织机构服务：树查询与维护
 */
@Service
@RequiredArgsConstructor
public class MdmOrgService {

    private final MdmMappers.OrgMapper orgMapper;
    private final MdmMappers.ProjectMapper projectMapper;

    /** 组织树 */
    public List<MdmOrg> tree() {
        List<MdmOrg> all = orgMapper.selectList(new LambdaQueryWrapper<MdmOrg>()
                .orderByAsc(MdmOrg::getSort).orderByAsc(MdmOrg::getId));
        Map<Long, List<MdmOrg>> byParent = all.stream()
                .filter(o -> o.getParentId() != null)
                .collect(Collectors.groupingBy(MdmOrg::getParentId));
        List<MdmOrg> roots = byParent.getOrDefault(0L, List.of());
        roots.forEach(r -> attach(r, byParent));
        return roots;
    }

    private void attach(MdmOrg node, Map<Long, List<MdmOrg>> byParent) {
        List<MdmOrg> children = byParent.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            children.forEach(c -> attach(c, byParent));
        }
    }

    @Transactional
    public Long create(MdmOrg org) {
        if (org.getName() == null || org.getName().isBlank()) throw new BizException("组织名称不能为空");
        if (org.getParentId() == null) org.setParentId(0L);
        if (org.getParentId() != 0 && orgMapper.selectById(org.getParentId()) == null)
            throw new BizException("上级组织不存在");
        if (org.getStatus() == null || org.getStatus().isBlank()) org.setStatus("ACTIVE");
        org.setId(null);
        orgMapper.insert(org);
        return org.getId();
    }

    @Transactional
    public void update(MdmOrg org) {
        if (org.getId() == null) throw new BizException("缺少组织ID");
        if (org.getName() == null || org.getName().isBlank()) throw new BizException("组织名称不能为空");
        if (org.getParentId() == null) org.setParentId(0L);
        // 防循环：新上级的祖先链不能经过自身
        Long pid = org.getParentId();
        while (pid != null && pid > 0) {
            if (pid.equals(org.getId())) throw new BizException("上级组织不能选择自身或其下级");
            MdmOrg p = orgMapper.selectById(pid);
            if (p == null) throw new BizException("上级组织不存在");
            pid = p.getParentId();
        }
        int updated = orgMapper.updateById(org);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 删除校验：有子组织或有项目挂接则禁止删除 */
    @Transactional
    public void delete(Long id) {
        Long childCnt = orgMapper.selectCount(new LambdaQueryWrapper<MdmOrg>().eq(MdmOrg::getParentId, id));
        if (childCnt > 0) throw new BizException("存在下级组织，无法删除");
        Long projCnt = projectMapper.selectCount(new LambdaQueryWrapper<MdmProject>().eq(MdmProject::getOrgId, id));
        if (projCnt > 0) throw new BizException("该组织下已挂接项目，无法删除");
        orgMapper.deleteById(id);
    }
}
