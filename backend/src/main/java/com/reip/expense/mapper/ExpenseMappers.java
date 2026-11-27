package com.reip.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.expense.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 费用模块 Mapper 聚合（模式同 SaleMappers / CostMappers）
 * 含预算原子回写、发票全平台查重、报表聚合、跨模块只读（mdm_org / sale_contract）
 */
public interface ExpenseMappers {

    @Mapper
    interface BudgetMapper extends BaseMapper<ExpBudget> {
        /** R1 原子占用：余额不足返回 0（防超占） */
        @Update("UPDATE exp_budget SET occupy_amount = occupy_amount + #{amt}, balance = balance - #{amt} "
                + "WHERE id = #{id} AND balance >= #{amt} AND deleted = 0")
        int occupy(@Param("id") Long id, @Param("amt") BigDecimal amt);

        /** 申请驳回：释放占用 */
        @Update("UPDATE exp_budget SET occupy_amount = GREATEST(occupy_amount - #{amt}, 0), "
                + "balance = balance + #{amt} WHERE id = #{id} AND deleted = 0")
        int releaseOccupy(@Param("id") Long id, @Param("amt") BigDecimal amt);

        /** R1 原子核销：余额不足返回 0 */
        @Update("UPDATE exp_budget SET writeoff_amount = writeoff_amount + #{amt}, balance = balance - #{amt} "
                + "WHERE id = #{id} AND balance >= #{amt} AND deleted = 0")
        int writeoff(@Param("id") Long id, @Param("amt") BigDecimal amt);

        /** 核销回退（报销驳回且未核销时无用；用于回滚） */
        @Update("UPDATE exp_budget SET writeoff_amount = GREATEST(writeoff_amount - #{amt}, 0), "
                + "balance = balance + #{amt} WHERE id = #{id} AND deleted = 0")
        int releaseWriteoff(@Param("id") Long id, @Param("amt") BigDecimal amt);
    }

    @Mapper
    interface StandardMapper extends BaseMapper<ExpStandard> {}

    @Mapper
    interface ApplyMapper extends BaseMapper<ExpApply> {}

    @Mapper
    interface LoanMapper extends BaseMapper<ExpLoan> {
        /** 冲销：未冲销余额原子扣减 */
        @Update("UPDATE exp_loan SET unwriteoff_balance = GREATEST(unwriteoff_balance - #{amt}, 0) "
                + "WHERE id = #{id} AND deleted = 0")
        int reduceBalance(@Param("id") Long id, @Param("amt") BigDecimal amt);

        /** 借款人未冲销余额合计（ACTIVE/APPROVING/DUE） */
        @Select("SELECT COALESCE(SUM(unwriteoff_balance), 0) FROM exp_loan "
                + "WHERE user_id = #{userId} AND status IN ('ACTIVE','APPROVING','DUE') AND deleted = 0")
        BigDecimal sumUnwriteoff(@Param("userId") Long userId);

        /** 借款人是否存在逾期(DUE)未冲销借款（R4 阻断） */
        @Select("SELECT COUNT(*) FROM exp_loan WHERE user_id = #{userId} AND status = 'DUE' "
                + "AND unwriteoff_balance > 0 AND deleted = 0")
        long countDueUnwriteoff(@Param("userId") Long userId);
    }

    @Mapper
    interface ReimburseMapper extends BaseMapper<ExpReimburse> {}

    @Mapper
    interface ReimburseItemMapper extends BaseMapper<ExpReimburseItem> {}

    @Mapper
    interface InvoiceMapper extends BaseMapper<ExpInvoice> {
        /** R2 全平台发票查重：费用池 duplicate_hash + 成本池 invoice_no（排除自身） */
        @Select("SELECT (SELECT COUNT(*) FROM exp_invoice WHERE duplicate_hash = #{hash} AND deleted = 0 "
                + "AND (#{excludeId} IS NULL OR id <> #{excludeId})) "
                + " + (SELECT COUNT(*) FROM cost_invoice WHERE invoice_no = #{invNo} AND deleted = 0)")
        long countDuplicate(@Param("hash") String hash, @Param("invNo") String invNo,
                            @Param("excludeId") Long excludeId);
    }

    @Mapper
    interface PaymentMapper extends BaseMapper<ExpPayment> {}

    @Mapper
    interface AllocationMapper extends BaseMapper<ExpAllocation> {}

    @Mapper
    interface ContractMapper extends BaseMapper<ExpContract> {
        /** 实付回写：已付+amt 超合同额返回 0（防超付） */
        @Update("UPDATE exp_contract SET paid_amount = paid_amount + #{amt} "
                + "WHERE id = #{id} AND deleted = 0 AND paid_amount + #{amt} <= amount")
        int addPaid(@Param("id") Long id, @Param("amt") BigDecimal amt);
    }

    @Mapper
    interface MatterMapper extends BaseMapper<ExpMatter> {}

    /** MDM 主数据只读查询（部门名称带出） */
    @Mapper
    interface MdmLookupMapper {
        @Select("SELECT name FROM mdm_org WHERE id = #{id} AND deleted = 0")
        String selectOrgName(Long id);
    }

    /** 费用报表聚合查询 */
    @Mapper
    interface ReportMapper {

        /** 预算执行 - 部门视角 */
        @Select("SELECT b.dept_id AS deptId, o.name AS deptName, "
                + "COALESCE(SUM(b.amount),0) AS budget, COALESCE(SUM(b.occupy_amount),0) AS occupy, "
                + "COALESCE(SUM(b.writeoff_amount),0) AS writeoff, COALESCE(SUM(b.balance),0) AS balance "
                + "FROM exp_budget b LEFT JOIN mdm_org o ON o.id = b.dept_id AND o.deleted = 0 "
                + "WHERE b.deleted = 0 GROUP BY b.dept_id, o.name ORDER BY budget DESC")
        List<Map<String, Object>> execByDept();

        /** 预算执行 - 科目视角 */
        @Select("SELECT COALESCE(account_name,'未分科目') AS accountName, "
                + "COALESCE(SUM(amount),0) AS budget, COALESCE(SUM(occupy_amount),0) AS occupy, "
                + "COALESCE(SUM(writeoff_amount),0) AS writeoff, COALESCE(SUM(balance),0) AS balance "
                + "FROM exp_budget WHERE deleted = 0 GROUP BY account_name ORDER BY budget DESC")
        List<Map<String, Object>> execByAccount();

        /** 预算执行 - 组织/项目公司视角（部门所属上级组织） */
        @Select("SELECT COALESCE(po.name,'集团') AS orgName, "
                + "COALESCE(SUM(b.amount),0) AS budget, COALESCE(SUM(b.occupy_amount),0) AS occupy, "
                + "COALESCE(SUM(b.writeoff_amount),0) AS writeoff, COALESCE(SUM(b.balance),0) AS balance "
                + "FROM exp_budget b JOIN mdm_org d ON d.id = b.dept_id AND d.deleted = 0 "
                + "LEFT JOIN mdm_org po ON po.id = d.parent_id AND po.deleted = 0 "
                + "WHERE b.deleted = 0 GROUP BY po.name ORDER BY budget DESC")
        List<Map<String, Object>> execByOrg();

        /** 预算执行 - 合计 */
        @Select("SELECT COALESCE(SUM(amount),0) AS budget, COALESCE(SUM(occupy_amount),0) AS occupy, "
                + "COALESCE(SUM(writeoff_amount),0) AS writeoff, COALESCE(SUM(balance),0) AS balance "
                + "FROM exp_budget WHERE deleted = 0")
        Map<String, Object> execTotal();

        /** 三费构成（按科目名称归类：销售/财务/管理） */
        @Select("SELECT "
                + "COALESCE(SUM(CASE WHEN i.account_name LIKE '%销售%' OR i.account_name LIKE '%市场%' "
                + "OR i.account_name LIKE '%推广%' THEN i.amount ELSE 0 END),0) AS salesFee, "
                + "COALESCE(SUM(CASE WHEN i.account_name LIKE '%财务%' OR i.account_name LIKE '%利息%' "
                + "THEN i.amount ELSE 0 END),0) AS finFee, "
                + "COALESCE(SUM(CASE WHEN i.account_name LIKE '%销售%' OR i.account_name LIKE '%市场%' "
                + "OR i.account_name LIKE '%推广%' OR i.account_name LIKE '%财务%' OR i.account_name LIKE '%利息%' "
                + "THEN 0 ELSE i.amount END),0) AS adminFee, "
                + "COALESCE(SUM(i.amount),0) AS total "
                + "FROM exp_reimburse_item i JOIN exp_reimburse r ON r.id = i.reimb_id AND r.deleted = 0 "
                + "WHERE i.deleted = 0 AND r.status IN ('APPROVED','PAID')")
        Map<String, Object> threeFee();

        /** 签约总额（费效比分母，跨模块只读 sale_contract） */
        @Select("SELECT COALESCE(SUM(contract_price),0) FROM sale_contract "
                + "WHERE deleted = 0 AND status IN ('ACTIVE','FILED')")
        BigDecimal signedTotal();

        /** 月度报销趋势（环比突增分析用） */
        @Select("SELECT DATE_FORMAT(created_at,'%Y-%m') AS month, COALESCE(SUM(total_amount),0) AS amt, "
                + "COUNT(*) AS cnt FROM exp_reimburse WHERE deleted = 0 AND status IN ('APPROVED','PAID') "
                + "GROUP BY month ORDER BY month")
        List<Map<String, Object>> monthlyTrend();

        /** 高频报销人（Top 5） */
        @Select("SELECT user_name AS userName, COUNT(*) AS cnt, COALESCE(SUM(total_amount),0) AS amt "
                + "FROM exp_reimburse WHERE deleted = 0 AND status IN ('APPROVED','PAID') "
                + "GROUP BY user_name ORDER BY cnt DESC, amt DESC LIMIT 5")
        List<Map<String, Object>> topReimbursers();

        /** 总览待办计数：审批中申请/报销、在借借款、待付付款、未验真发票 */
        @Select("SELECT "
                + "(SELECT COUNT(*) FROM exp_apply WHERE status = 'APPROVING' AND deleted = 0) AS applyApproving, "
                + "(SELECT COUNT(*) FROM exp_reimburse WHERE status = 'APPROVING' AND deleted = 0) AS reimbApproving, "
                + "(SELECT COUNT(*) FROM exp_loan WHERE status IN ('ACTIVE','DUE') AND deleted = 0) AS loanActive, "
                + "(SELECT COUNT(*) FROM exp_payment WHERE status = 'DRAFT' AND deleted = 0) AS paymentDraft, "
                + "(SELECT COUNT(*) FROM exp_invoice WHERE status = 'ACTIVE' AND verify_status = 'UNVERIFIED' AND deleted = 0) AS invoiceNew")
        Map<String, Object> counts();
    }
}
