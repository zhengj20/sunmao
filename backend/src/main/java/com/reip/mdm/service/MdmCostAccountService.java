package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.mdm.entity.MdmCostAccount;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 成本科目服务：集团模板树、项目克隆树、科目 CRUD
 */
@Service
@RequiredArgsConstructor
public class MdmCostAccountService {

    private final MdmMappers.CostAccountMapper costAccountMapper;
    private final MdmMappers.ProjectMapper projectMapper;

    /** 科目树：projectId 为空返回集团模板树，否则返回项目克隆树 */
    public List<MdmCostAccount> tree(Long projectId) {
        LambdaQueryWrapper<MdmCostAccount> qw = new LambdaQueryWrapper<MdmCostAccount>()
                .orderByAsc(MdmCostAccount::getSort).orderByAsc(MdmCostAccount::getCode).orderByAsc(MdmCostAccount::getId);
        if (projectId == null) qw.isNull(MdmCostAccount::getProjectId);
        else qw.eq(MdmCostAccount::getProjectId, projectId);
        List<MdmCostAccount> all = costAccountMapper.selectList(qw);
        Map<Long, List<MdmCostAccount>> byParent = all.stream()
                .filter(a -> a.getParentId() != null)
                .collect(Collectors.groupingBy(MdmCostAccount::getParentId));
        // 根节点兼容 parent_id 为 NULL 或 0（V8 克隆种子用 NULL，模板/cloneToProject 用 0）
        List<MdmCostAccount> roots = all.stream()
                .filter(a -> a.getParentId() == null || a.getParentId() == 0L)
                .collect(Collectors.toList());
        roots.forEach(r -> attach(r, byParent));
        return roots;
    }

    private void attach(MdmCostAccount node, Map<Long, List<MdmCostAccount>> byParent) {
        List<MdmCostAccount> children = byParent.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            children.forEach(c -> attach(c, byParent));
        }
    }

    /**
     * 集团模板整棵克隆到项目下：保持父子层级、生成新 id、保留末级 leaf 标记
     */
    @Transactional
    public Integer cloneToProject(Long projectId) {
        if (projectId == null) throw new BizException("缺少项目ID");
        if (projectMapper.selectById(projectId) == null) throw new BizException("项目不存在");
        Long cnt = costAccountMapper.selectCount(new LambdaQueryWrapper<MdmCostAccount>()
                .eq(MdmCostAccount::getProjectId, projectId));
        if (cnt > 0) throw new BizException("该项目已克隆过成本科目，请勿重复克隆");

        List<MdmCostAccount> templates = costAccountMapper.selectList(new LambdaQueryWrapper<MdmCostAccount>()
                .isNull(MdmCostAccount::getProjectId)
                .orderByAsc(MdmCostAccount::getLevel).orderByAsc(MdmCostAccount::getSort).orderByAsc(MdmCostAccount::getId));
        // 旧id→新id 映射，按级次排序保证父节点先插入
        Map<Long, Long> idMapping = new HashMap<>();
        for (MdmCostAccount t : templates) {
            MdmCostAccount copy = new MdmCostAccount();
            copy.setProjectId(projectId);
            copy.setParentId(t.getParentId() == null || t.getParentId() == 0 ? 0L
                    : idMapping.getOrDefault(t.getParentId(), 0L));
            copy.setCode(t.getCode());
            copy.setName(t.getName());
            copy.setLevel(t.getLevel());
            copy.setLeaf(t.getLeaf());
            copy.setControlMode(t.getControlMode());
            copy.setAllocateRule(t.getAllocateRule());
            copy.setFinanceAccount(t.getFinanceAccount());
            copy.setSort(t.getSort());
            costAccountMapper.insert(copy);
            idMapping.put(t.getId(), copy.getId());
        }
        return templates.size();
    }

    @Transactional
    public Long create(MdmCostAccount account) {
        if (account.getCode() == null || account.getCode().isBlank()) throw new BizException("科目编码不能为空");
        if (account.getName() == null || account.getName().isBlank()) throw new BizException("科目名称不能为空");
        if (account.getParentId() == null) account.setParentId(0L);
        if (account.getLeaf() == null) account.setLeaf(1);
        if (account.getParentId() == 0) {
            account.setLevel(1);
        } else {
            MdmCostAccount parent = costAccountMapper.selectById(account.getParentId());
            if (parent == null) throw new BizException("上级科目不存在");
            // 同一棵树内挂接：模板只能挂模板，项目科目只能挂项目科目
            if (!Objects.equals(parent.getProjectId(), account.getProjectId()))
                throw new BizException("上级科目与当前科目不属于同一棵科目树");
            account.setLevel(parent.getLevel() == null ? 2 : parent.getLevel() + 1);
            // 挂上子节点后父节点不再是末级
            if (parent.getLeaf() != null && parent.getLeaf() == 1) {
                MdmCostAccount up = new MdmCostAccount();
                up.setId(parent.getId());
                up.setVersion(parent.getVersion());
                up.setLeaf(0);
                costAccountMapper.updateById(up);
            }
        }
        account.setId(null);
        costAccountMapper.insert(account);
        return account.getId();
    }

    @Transactional
    public void update(MdmCostAccount account) {
        if (account.getId() == null) throw new BizException("缺少科目ID");
        account.setProjectId(null); // 不可跨树移动
        int updated = costAccountMapper.updateById(account);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 科目删除：有子节点禁删；父节点无其他子节点时恢复末级标记 */
    @Transactional
    public void delete(Long id) {
        MdmCostAccount account = costAccountMapper.selectById(id);
        if (account == null) throw new BizException("科目不存在");
        Long childCnt = costAccountMapper.selectCount(new LambdaQueryWrapper<MdmCostAccount>()
                .eq(MdmCostAccount::getParentId, id));
        if (childCnt > 0) throw new BizException("存在下级科目，无法删除");
        costAccountMapper.deleteById(id);
        if (account.getParentId() != null && account.getParentId() > 0) {
            Long siblings = costAccountMapper.selectCount(new LambdaQueryWrapper<MdmCostAccount>()
                    .eq(MdmCostAccount::getParentId, account.getParentId()));
            if (siblings == 0) {
                MdmCostAccount parent = costAccountMapper.selectById(account.getParentId());
                if (parent != null) {
                    MdmCostAccount up = new MdmCostAccount();
                    up.setId(parent.getId());
                    up.setVersion(parent.getVersion());
                    up.setLeaf(1);
                    costAccountMapper.updateById(up);
                }
            }
        }
    }
}
