package com.reip.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.expense.entity.ExpMatter;
import com.reip.expense.mapper.ExpenseMappers;
import com.reip.framework.auth.UserContext;
import com.reip.framework.coding.CodeRuleService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事项申请：登记 / 提交审批(EXP_MATTER) / 审批回写
 */
@Service
@RequiredArgsConstructor
public class ExpMatterService {

    private final ExpenseMappers.MatterMapper matterMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;

    public PageResult<ExpMatter> page(long current, long size, Long deptId, String type, String status, String kw) {
        LambdaQueryWrapper<ExpMatter> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(ExpMatter::getDeptId, deptId);
        if (type != null && !type.isBlank()) qw.eq(ExpMatter::getType, type);
        if (status != null && !status.isBlank()) qw.eq(ExpMatter::getStatus, status);
        if (kw != null && !kw.isBlank())
            qw.and(w -> w.like(ExpMatter::getMatterNo, kw).or().like(ExpMatter::getTitle, kw));
        qw.orderByDesc(ExpMatter::getId);
        Page<ExpMatter> p = matterMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public ExpMatter detail(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(ExpMatter m) {
        if (m == null) throw new BizException("缺少申请内容");
        if (m.getType() == null || m.getType().isBlank())
            throw new BizException("请选择事项类型(MEETING/ACTIVITY/RENOVATE/PROCURE/OFFICE/OTHER)");
        if (m.getTitle() == null || m.getTitle().isBlank()) throw new BizException("请填写事项标题");
        if (m.getDeptId() == null) throw new BizException("请选择部门");
        m.setId(null);
        m.setMatterNo(codeRuleService.next("SX", Map.of()));
        if (m.getUserId() == null) m.setUserId(UserContext.require().getUserId());
        if (m.getUserName() == null || m.getUserName().isBlank()) m.setUserName(UserContext.require().getRealName());
        m.setStatus("DRAFT");
        matterMapper.insert(m);
        return m.getId();
    }

    @Transactional
    public void update(ExpMatter m) {
        if (m == null || m.getId() == null) throw new BizException("缺少事项ID");
        ExpMatter db = require(m.getId());
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的事项可修改");
        m.setMatterNo(null);
        m.setStatus(db.getStatus());
        if (matterMapper.updateById(m) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        ExpMatter db = require(id);
        if (!"DRAFT".equals(db.getStatus()) && !"REJECTED".equals(db.getStatus()))
            throw new BizException("仅草稿或驳回状态的事项可删除");
        matterMapper.deleteById(id);
    }

    /** 提交审批（bizType=EXP_MATTER）：部门→财务 */
    @Transactional
    public void submit(Long id) {
        ExpMatter m = require(id);
        if (!"DRAFT".equals(m.getStatus()) && !"REJECTED".equals(m.getStatus()))
            throw new BizException("当前状态不可提交申请");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("事项编号", m.getMatterNo());
        snapshot.put("类型", m.getType());
        snapshot.put("标题", m.getTitle());
        snapshot.put("部门", m.getDeptId());
        snapshot.put("预计金额", m.getExpectAmount());
        workflowClient.start("EXP_MATTER", id, "事项申请 " + m.getMatterNo(), m.getExpectAmount(), snapshot);
        m.setStatus("APPROVING");
        if (matterMapper.updateById(m) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    // ==================== 工作流回调 ====================

    /** 审批通过：APPROVED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(Long id) {
        ExpMatter m = matterMapper.selectById(id);
        if (m == null || !"APPROVING".equals(m.getStatus())) return; // 幂等
        m.setStatus("APPROVED");
        matterMapper.updateById(m);
    }

    /** 审批驳回：REJECTED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(Long id) {
        ExpMatter m = matterMapper.selectById(id);
        if (m == null || !"APPROVING".equals(m.getStatus())) return; // 幂等
        m.setStatus("REJECTED");
        matterMapper.updateById(m);
    }

    private ExpMatter require(Long id) {
        if (id == null) throw new BizException("缺少事项ID");
        ExpMatter m = matterMapper.selectById(id);
        if (m == null) throw new BizException("事项不存在");
        return m;
    }
}
