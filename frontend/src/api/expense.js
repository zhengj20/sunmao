import request from './request'

// =====================================================
// 费用管理 API（严格对齐后端 /api/expense/** 真实端点）
// 后端：预算/标准/申请/借款/报销/发票池/付款/分摊/报表
// 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data；
// code!==0 时 request.js 会 ElMessage.error(msg) 并 reject —— 前端不吞错即可展示后端提示
//   如 R1 超预算阻断 / R2 发票重复 / R3 超标须说明 / R4 借款额度·逾期阻断
// =====================================================

// ================= 公共选项（复用 MDM 已有接口） =================
/** 部门下拉：mdm 组织树拍平为 { id, name }（默认取第一个） */
export const fetchOrgOptions = async () => {
  const tree = (await request.get('/mdm/orgs/tree').catch(() => [])) || []
  const out = []
  const walk = list => (list || []).forEach(n => { out.push({ id: n.id, name: n.name }); walk(n.children) })
  walk(tree)
  return out
}
/** 员工下拉：mdm 员工分页，取 { userId, realName }（申请人/借款人/报销人） */
export const fetchEmployeeOptions = async (kw = '') => {
  const page = await request.get('/mdm/employees/page', { params: { current: 1, size: 200, kw } }).catch(() => null)
  return (page?.records || []).map(e => ({ userId: e.userId, name: e.realName || e.userName || ('员工#' + e.userId) }))
}
/** 费用科目下拉：mdm 费用科目树拍平（取末级 leaf）{ id, name, code, controlMode } */
export const fetchAccountOptions = async () => {
  const tree = (await request.get('/mdm/expense-accounts/tree').catch(() => [])) || []
  const out = []
  const walk = list => (list || []).forEach(n => { if (n.leaf) out.push({ id: n.id, name: n.name, code: n.code, controlMode: n.controlMode }); walk(n.children) })
  walk(tree)
  return out
}
/** 项目下拉：mdm 项目选项 */
export const fetchProjectOptions = () => request.get('/mdm/projects/options')

// ================= 状态与字典 =================
/** 预算状态 */
export const BUDGET_STATUS = { ACTIVE: ['生效', 'success'], ARCHIVED: ['已归档', 'info'] }
/** 事前申请状态 */
export const APPLY_STATUS = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'],
  ACTIVE: ['生效', 'success'], REJECTED: ['已驳回', 'danger']
}
/** 借款状态 */
export const LOAN_STATUS = {
  APPROVING: ['审批中', 'warning'], ACTIVE: ['在借', 'success'],
  WRITEOFF: ['已冲销', 'primary'], DUE: ['逾期', 'danger']
}
/** 报销状态 */
export const REIMB_STATUS = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'], APPROVED: ['已通过', 'success'],
  REJECTED: ['已驳回', 'danger'], PAID: ['已付款', 'primary']
}
/** 发票验真状态 */
export const VERIFY_STATUS = { UNVERIFIED: ['未验真', 'info'], PASS: ['验真通过', 'success'], FAIL: ['验真失败', 'danger'] }
/** 付款状态 */
export const PAY_STATUS = { DRAFT: ['待付', 'warning'], PAID: ['已付', 'success'] }

/** 事前申请类型 */
export const APPLY_TYPES = [
  { value: 'TRAVEL', label: '出差' }, { value: 'HOST', label: '招待' }, { value: 'PROCURE', label: '采购' }
]
export const applyTypeLabel = v => APPLY_TYPES.find(t => t.value === v)?.label || v || '-'
/** 报销类型 */
export const REIMB_TYPES = [{ value: 'TRAVEL', label: '差旅' }, { value: 'DAILY', label: '日常' }]
export const reimbTypeLabel = v => REIMB_TYPES.find(t => t.value === v)?.label || v || '-'
/** 费控标准类型 */
export const STD_TYPES = [
  { value: 'TRAVEL', label: '住宿交通' }, { value: 'HOST', label: '业务招待' },
  { value: 'COMMS', label: '通讯' }, { value: 'CAR', label: '车辆' }
]
export const stdTypeLabel = v => STD_TYPES.find(t => t.value === v)?.label || v || '-'
/** 发票来源 */
export const INV_SOURCES = [
  { value: 'REIMBURSE', label: '报销' }, { value: 'COST', label: '成本' }, { value: 'PROC', label: '采购' }
]
export const invSourceLabel = v => INV_SOURCES.find(t => t.value === v)?.label || v || '-'
/** 职级选项 */
export const STD_LEVELS = ['E5', 'E6', 'E7', 'M3', 'M4', 'M5']
/** 城市等级选项 */
export const STD_CITY_LEVELS = ['一线', '二线', '三线', '全部']

/** 取 [中文名, tagType]，兜底 [原值, 'info'] */
export const statusMeta = (s, map) => {
  const m = (map && map[s]) || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}

// ================= 费用预算 /api/expense/budgets =================
export const budgetPage = params => request.get('/expense/budgets/page', { params })
/** 四栏台账（不分页，List）：{ deptId, year } */
export const budgetLedger = params => request.get('/expense/budgets/ledger', { params })
export const budgetDetail = id => request.get(`/expense/budgets/${id}`)
/** 预算编制：{ deptId, accountId, accountName, budgetYear, month, amount } */
export const budgetCreate = data => request.post('/expense/budgets', data)
export const budgetUpdate = data => request.put('/expense/budgets', data)
export const budgetDelete = id => request.delete(`/expense/budgets/${id}`)

// ================= 费控标准 /api/expense/standards =================
export const standardPage = params => request.get('/expense/standards/page', { params })
export const standardDetail = id => request.get(`/expense/standards/${id}`)
/** 新增标准：{ level, cityLevel, type, limitAmount } */
export const standardCreate = data => request.post('/expense/standards', data)
export const standardUpdate = data => request.put('/expense/standards', data)
export const standardDelete = id => request.delete(`/expense/standards/${id}`)

// ================= 费用申请 /api/expense/applies =================
export const applyPage = params => request.get('/expense/applies/page', { params })
export const applyDetail = id => request.get(`/expense/applies/${id}`)
/** 新建申请：{ type, deptId, userId, userName, amount, budgetId, remark }（DRAFT） */
export const applyCreate = data => request.post('/expense/applies', data)
export const applyUpdate = data => request.put('/expense/applies', data)
export const applyDelete = id => request.delete(`/expense/applies/${id}`)
/** 提交审批：R1 预算占用硬校验 + 发起 EXP_BUDGET 审批 —— 超预算后端报错，request.js 已提示 */
export const applySubmit = id => request.post(`/expense/applies/${id}/submit`)

// ================= 借款 /api/expense/loans =================
export const loanPage = params => request.get('/expense/loans/page', { params })
export const loanDetail = id => request.get(`/expense/loans/${id}`)
/** 登记并发起借款审批：{ userId, userName, amount, borrowDate, dueDate }（R4 额度/逾期阻断） */
export const loanCreate = data => request.post('/expense/loans', data)
/** 冲销/还款：{ amount }（未冲销余额扣减，清零置 WRITEOFF） */
export const loanWriteoff = (id, amount) => request.post(`/expense/loans/${id}/writeoff`, { amount })

// ================= 报销 /api/expense/reimburses =================
export const reimbursePage = params => request.get('/expense/reimburses/page', { params })
export const reimburseDetail = id => request.get(`/expense/reimburses/${id}`)
/** 登记报销（带明细行）：{ type, deptId, userId, userName, totalAmount, overReason, applyId, loanId, invoiceId, items:[{accountId,accountName,amount,remark}] } */
export const reimburseCreate = data => request.post('/expense/reimburses', data)
/** 提交：超标走 EXP_REIMB_OVERRUN 特批（R3 须说明）；未超标直接生效并核销 */
export const reimburseSubmit = id => request.post(`/expense/reimburses/${id}/submit`)

// ================= 发票池 /api/expense/invoices =================
export const invoicePage = params => request.get('/expense/invoices/page', { params })
export const invoiceDetail = id => request.get(`/expense/invoices/${id}`)
/** 登记发票：{ invNo, taxNo, supplier, amount, taxAmount, source }（R2 全平台查重） */
export const invoiceCreate = data => request.post('/expense/invoices', data)
export const invoiceUpdate = data => request.put('/expense/invoices', data)
export const invoiceDelete = id => request.delete(`/expense/invoices/${id}`)
/** 验真（占位，待接 ai-service）→ 返回更新后发票 { verifyStatus: PASS/FAIL } */
export const invoiceVerify = id => request.post(`/expense/invoices/${id}/verify`)
/** OCR 识别（占位，待接 ai-service）→ { recognized:false, message } */
export const invoiceOcr = input => request.post('/expense/invoices/ocr', input || {})

// ================= 费用付款 /api/expense/payments =================
export const paymentPage = params => request.get('/expense/payments/page', { params })
export const paymentDetail = id => request.get(`/expense/payments/${id}`)
/** 为审批通过的报销生成付款单：{ reimbId, amount } */
export const paymentCreate = data => request.post('/expense/payments', data)
/** 实付登记：{ amount, paidDate } → 付款单 PAID + 报销单回写 PAID */
export const paymentPay = (id, form) => request.post(`/expense/payments/${id}/pay`, form || {})

// ================= 费用分摊 /api/expense/allocations =================
export const allocationPage = params => request.get('/expense/allocations/page', { params })
export const allocationDetail = id => request.get(`/expense/allocations/${id}`)
/** 登记分摊：{ month, ruleText, deptId, projectId, amount } */
export const allocationCreate = data => request.post('/expense/allocations', data)
export const allocationDelete = id => request.delete(`/expense/allocations/${id}`)
/** 月度分摊计算：{ month, total, deptIds:[] } → 返回生成条数 */
export const allocationCalculate = form => request.post('/expense/allocations/calculate', form || {})

// ================= 费用合同 /api/expense/contracts =================
export const contractPage = params => request.get('/expense/contracts/page', { params })
export const contractDetail = id => request.get(`/expense/contracts/${id}`)
/** 新建合同：{ name, supplier, amount, signDate, startDate, endDate, remark } */
export const contractCreate = data => request.post('/expense/contracts', data)
export const contractUpdate = data => request.put('/expense/contracts', data)
export const contractDelete = id => request.delete(`/expense/contracts/${id}`)
/** 状态维护：ACTIVE/EXPIRED/TERMINATED */
export const contractStatus = (id, status) => request.put(`/expense/contracts/${id}/status/${status}`)
/** 付款单关联下拉（生效中合同） */
export const contractOptions = () => request.get('/expense/contracts/options')
/** 合同付款记录 */
export const contractPayments = id => request.get(`/expense/contracts/${id}/payments`)
/** 为合同新建付款单（DRAFT）：{ amount? }（默认合同未付余额） */
export const contractPaymentCreate = (id, form) => request.post(`/expense/contracts/${id}/payments`, form || {})
export const CONTRACT_STATUS = { ACTIVE: ['生效', 'success'], EXPIRED: ['已到期', 'info'], TERMINATED: ['已终止', 'danger'] }

// ================= 事项申请 /api/expense/matters =================
export const matterPage = params => request.get('/expense/matters/page', { params })
export const matterDetail = id => request.get(`/expense/matters/${id}`)
/** 新建事项：{ type, title, description, deptId, userId, expectAmount, planDate }（DRAFT） */
export const matterCreate = data => request.post('/expense/matters', data)
export const matterUpdate = data => request.put('/expense/matters', data)
export const matterDelete = id => request.delete(`/expense/matters/${id}`)
/** 提交审批（EXP_MATTER：部门→财务） */
export const matterSubmit = id => request.post(`/expense/matters/${id}/submit`)
/** 事项类型 */
export const MATTER_TYPES = [
  { value: 'MEETING', label: '会议' }, { value: 'ACTIVITY', label: '活动' },
  { value: 'RENOVATE', label: '装修' }, { value: 'PROCURE', label: '采购' },
  { value: 'OFFICE', label: '办公' }, { value: 'OTHER', label: '其他' }
]
export const matterTypeLabel = v => MATTER_TYPES.find(t => t.value === v)?.label || v || '-'
export const MATTER_STATUS = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'],
  APPROVED: ['已通过', 'success'], REJECTED: ['已驳回', 'danger']
}

// ================= 费用报表 /api/expense/reports =================
/** 预算执行：{ byDept:[], byAccount:[], byOrg:[], total:{budget,occupy,writeoff,balance} } */
export const reportExec = () => request.get('/expense/reports/exec')
/** 三费占比·费效比：{ total, salesFee, adminFee, finFee, salesRatio, adminRatio, finRatio, signedTotal, costEfficiencyRatio } */
export const reportThreeFee = () => request.get('/expense/reports/three-fee')
/** 异常识别：{ monthlyTrend:[], surges:[], topReimbursers:[] } */
export const reportAnomaly = () => request.get('/expense/reports/anomaly')
/** 费用总览：{ budget:{...}, counts:{...}, monthExpense, trend:[], topDept:[] } */
export const reportOverview = () => request.get('/expense/reports/overview')
