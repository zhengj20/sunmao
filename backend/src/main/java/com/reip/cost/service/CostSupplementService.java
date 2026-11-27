package com.reip.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reip.common.exception.BizException;
import com.reip.common.result.PageResult;
import com.reip.cost.entity.CostContract;
import com.reip.cost.entity.CostSupplement;
import com.reip.cost.mapper.CostMappers;
import com.reip.framework.coding.CodeRuleService;
import com.reip.workflow.api.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合同补充协议：CRUD / 审批 / 回写合同金额
 */
@Service
@RequiredArgsConstructor
public class CostSupplementService {

    private final CostMappers.SupplementMapper supplementMapper;
    private final CostMappers.ContractMapper contractMapper;
    private final CodeRuleService codeRuleService;
    private final WorkflowClient workflowClient;
    private final CostSupport support;

    public PageResult<CostSupplement> page(long current, long size, Long projectId, Long contractId, String status, String kw) {
        LambdaQueryWrapper<CostSupplement> qw = new LambdaQueryWrapper<>();
        if (projectId != null) qw.eq(CostSupplement::getProjectId, projectId);
        if (contractId != null) qw.eq(CostSupplement::getContractId, contractId);
        if (status != null && !status.isBlank()) qw.eq(CostSupplement::getStatus, status);
        if (kw != null && !kw.isBlank()) qw.and(w -> w.like(CostSupplement::getTitle, kw).or().like(CostSupplement::getSupplementCode, kw));
        qw.orderByDesc(CostSupplement::getId);
        Page<CostSupplement> p = supplementMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public CostSupplement detail(Long id) {
        CostSupplement s = supplementMapper.selectById(id);
        if (s == null) throw new BizException("补充协议不存在");
        return s;
    }

    @Transactional
    public Long create(CostSupplement supplement) {
        if (supplement == null) throw new BizException("缺少表单内容");
        if (supplement.getContractId() == null) throw new BizException("请选择关联合同");
        if (supplement.getTitle() == null || supplement.getTitle().isBlank()) throw new BizException("请填写协议标题");
        if (supplement.getAmount() == null) throw new BizException("请填写补充金额");
        CostContract contract = contractMapper.selectById(supplement.getContractId());
        if (contract == null) throw new BizException("关联合同不存在");
        supplement.setProjectId(contract.getProjectId());
        supplement.setId(null);
        supplement.setSupplementCode(codeRuleService.next("BC", support.codeCtx(contract.getProjectId())));
        supplement.setStatus("DRAFT");
        supplementMapper.insert(supplement);
        return supplement.getId();
    }

    @Transactional
    public void update(CostSupplement supplement) {
        if (supplement == null || supplement.getId() == null) throw new BizException("缺少补充协议ID");
        CostSupplement db = detail(supplement.getId());
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态可修改");
        supplement.setSupplementCode(null);
        supplement.setProjectId(db.getProjectId());
        supplement.setStatus(db.getStatus());
        if (supplementMapper.updateById(supplement) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional
    public void delete(Long id) {
        CostSupplement db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态可删除");
        supplementMapper.deleteById(id);
    }

    @Transactional
    public void submit(Long id) {
        CostSupplement db = detail(id);
        if (!"DRAFT".equals(db.getStatus())) throw new BizException("仅草稿状态可提交审批");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("supplementCode", db.getSupplementCode());
        snapshot.put("title", db.getTitle());
        snapshot.put("amount", db.getAmount());
        snapshot.put("contractId", db.getContractId());
        snapshot.put("projectId", db.getProjectId());
        workflowClient.start("COST_SUPPLEMENT", id, "补充协议 " + db.getSupplementCode(), db.getAmount(), snapshot);
        CostSupplement up = new CostSupplement();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("APPROVING");
        if (supplementMapper.updateById(up) == 0) throw new BizException("数据已被他人修改，请刷新后重试");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowApproved(Long id) {
        CostSupplement db = supplementMapper.selectById(id);
        if (db == null || !"APPROVING".equals(db.getStatus())) return;
        CostSupplement up = new CostSupplement();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("APPROVED");
        supplementMapper.updateById(up);
        // 回写合同签约金额
        if (db.getContractId() != null && db.getAmount() != null) {
            CostContract contract = contractMapper.selectById(db.getContractId());
            if (contract != null) {
                CostContract cup = new CostContract();
                cup.setId(contract.getId());
                cup.setVersion(contract.getVersion());
                cup.setSignedAmount(contract.getSignedAmount().add(db.getAmount()));
                contractMapper.updateById(cup);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkflowRejected(Long id) {
        CostSupplement db = supplementMapper.selectById(id);
        if (db == null || !"APPROVING".equals(db.getStatus())) return;
        CostSupplement up = new CostSupplement();
        up.setId(id);
        up.setVersion(db.getVersion());
        up.setStatus("REJECTED");
        supplementMapper.updateById(up);
    }
}
