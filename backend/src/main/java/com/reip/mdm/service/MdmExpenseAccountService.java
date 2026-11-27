package com.reip.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reip.common.exception.BizException;
import com.reip.mdm.entity.MdmExpenseAccount;
import com.reip.mdm.mapper.MdmMappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 费用科目服务：三费(管理/销售/财务)科目树、科目 CRUD（与成本科目分离）
 */
@Service
@RequiredArgsConstructor
public class MdmExpenseAccountService {

    private final MdmMappers.ExpenseAccountMapper expenseAccountMapper;

    /** 费用科目树（集团级，无项目克隆） */
    public List<MdmExpenseAccount> tree() {
        List<MdmExpenseAccount> all = expenseAccountMapper.selectList(new LambdaQueryWrapper<MdmExpenseAccount>()
                .orderByAsc(MdmExpenseAccount::getSort).orderByAsc(MdmExpenseAccount::getCode).orderByAsc(MdmExpenseAccount::getId));
        Map<Long, List<MdmExpenseAccount>> byParent = all.stream()
                .filter(a -> a.getParentId() != null)
                .collect(Collectors.groupingBy(MdmExpenseAccount::getParentId));
        List<MdmExpenseAccount> roots = all.stream()
                .filter(a -> a.getParentId() == null || a.getParentId() == 0L)
                .collect(Collectors.toList());
        roots.forEach(r -> attach(r, byParent));
        return roots;
    }

    private void attach(MdmExpenseAccount node, Map<Long, List<MdmExpenseAccount>> byParent) {
        List<MdmExpenseAccount> children = byParent.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            children.forEach(c -> attach(c, byParent));
        }
    }

    @Transactional
    public Long create(MdmExpenseAccount account) {
        if (account.getCode() == null || account.getCode().isBlank()) throw new BizException("科目编码不能为空");
        if (account.getName() == null || account.getName().isBlank()) throw new BizException("科目名称不能为空");
        if (account.getParentId() == null) account.setParentId(0L);
        if (account.getLeaf() == null) account.setLeaf(1);
        if (account.getParentId() == 0) {
            account.setLevel(1);
        } else {
            MdmExpenseAccount parent = expenseAccountMapper.selectById(account.getParentId());
            if (parent == null) throw new BizException("上级科目不存在");
            account.setLevel(parent.getLevel() == null ? 2 : parent.getLevel() + 1);
            // 挂上子节点后父节点不再是末级
            if (parent.getLeaf() != null && parent.getLeaf() == 1) {
                MdmExpenseAccount up = new MdmExpenseAccount();
                up.setId(parent.getId());
                up.setVersion(parent.getVersion());
                up.setLeaf(0);
                expenseAccountMapper.updateById(up);
            }
        }
        account.setId(null);
        expenseAccountMapper.insert(account);
        return account.getId();
    }

    @Transactional
    public void update(MdmExpenseAccount account) {
        if (account.getId() == null) throw new BizException("缺少科目ID");
        int updated = expenseAccountMapper.updateById(account);
        if (updated == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    /** 科目删除：有子节点禁删；父节点无其他子节点时恢复末级标记 */
    @Transactional
    public void delete(Long id) {
        MdmExpenseAccount account = expenseAccountMapper.selectById(id);
        if (account == null) throw new BizException("科目不存在");
        Long childCnt = expenseAccountMapper.selectCount(new LambdaQueryWrapper<MdmExpenseAccount>()
                .eq(MdmExpenseAccount::getParentId, id));
        if (childCnt > 0) throw new BizException("存在下级科目，无法删除");
        expenseAccountMapper.deleteById(id);
        if (account.getParentId() != null && account.getParentId() > 0) {
            Long siblings = expenseAccountMapper.selectCount(new LambdaQueryWrapper<MdmExpenseAccount>()
                    .eq(MdmExpenseAccount::getParentId, account.getParentId()));
            if (siblings == 0) {
                MdmExpenseAccount parent = expenseAccountMapper.selectById(account.getParentId());
                if (parent != null) {
                    MdmExpenseAccount up = new MdmExpenseAccount();
                    up.setId(parent.getId());
                    up.setVersion(parent.getVersion());
                    up.setLeaf(1);
                    expenseAccountMapper.updateById(up);
                }
            }
        }
    }
}
