import request from './request'

// =====================================================
// 全面预算 API（严格对齐后端 /api/budget/** 真实端点）
// 管控链：年度全面预算 → 月度全面预算 → 资金计划（成本线/费用线）
//   B1 年度控制月度（Σ月度 ≤ 年度，硬控）
//   B2 年度下调约束（≥ 已分解/已执行）
//   B3 月度控制成本资金计划（成本付款申请按合同科目校验，刚性阻断/预警放行）
//   B4 月度控制费用资金计划（费用预算编制/调整校验）
//   B5 资金计划一致性（成本=合同付款计划、费用=当月费用预算，超出→资金缺口）
// 后端错误（超预算阻断等）由 request.js 统一 ElMessage.error 提示
// =====================================================

// ================= 状态与字典 =================
/** 年度预算状态 */
export const BUD_STATUS = { DRAFT: ['草稿', 'info'], APPROVED: ['生效', 'success'], ARCHIVED: ['已归档', 'warning'] }
/** 预算类型 */
export const BUD_TYPES = [
  { value: 'COST', label: '成本线' }, { value: 'EXPENSE', label: '费用线' }
]
export const budTypeLabel = v => BUD_TYPES.find(t => t.value === v)?.label || v || '-'
/** 控制方式 */
export const CONTROL_MODES = ['刚性', '预警', '不限']
export const controlTag = v => (v === '刚性' ? 'danger' : v === '预警' ? 'warning' : 'info')
/** 调整方向 */
export const ADJUST_TYPES = [
  { value: 'INCREASE', label: '调增' }, { value: 'DECREASE', label: '调减' }
]
export const adjustTypeLabel = v => ADJUST_TYPES.find(t => t.value === v)?.label || v || '-'

export const statusMeta = (s, map) => {
  const m = (map && map[s]) || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}

// ================= 公共选项（复用 MDM 已有接口） =================
/** 项目下拉 */
export const fetchProjectOptions = () => request.get('/mdm/projects/options')
/** 部门下拉：mdm 组织树拍平 */
export const fetchOrgOptions = async () => {
  const tree = (await request.get('/mdm/orgs/tree').catch(() => [])) || []
  const out = []
  const walk = list => (list || []).forEach(n => { out.push({ id: n.id, name: n.name }); walk(n.children) })
  walk(tree)
  return out
}
/** 成本科目下拉：mdm 成本科目树拍平（末级）—— 年度预算成本线用 */
export const fetchAccountOptions = async () => {
  const tree = (await request.get('/mdm/accounts/tree').catch(() => [])) || []
  const out = []
  const walk = list => (list || []).forEach(n => { if (n.leaf) out.push({ id: n.id, name: n.name, code: n.code, controlMode: n.controlMode }); walk(n.children) })
  walk(tree)
  return out
}
/** 费用科目下拉：mdm 费用科目树拍平（末级）—— 年度预算费用线用 */
export const fetchExpenseAccountOptions = async () => {
  const tree = (await request.get('/mdm/expense-accounts/tree').catch(() => [])) || []
  const out = []
  const walk = list => (list || []).forEach(n => { if (n.leaf) out.push({ id: n.id, name: n.name, code: n.code, controlMode: n.controlMode }); walk(n.children) })
  walk(tree)
  return out
}

// ================= 年度预算 /api/budget/annual =================
export const annualPage = params => request.get('/budget/annual/page', { params })
/** 详情：{ budget, recentAdjusts } */
export const annualDetail = id => request.get(`/budget/annual/${id}`)
/** 编制：{ budgetYear, budgetType, projectId|deptId, accountId, accountName, annualAmount, controlMode, remark } */
export const annualCreate = data => request.post('/budget/annual', data)
/** 生效（DRAFT→APPROVED） */
export const annualActivate = id => request.post(`/budget/annual/${id}/activate`)
/** 归档 */
export const annualArchive = id => request.post(`/budget/annual/${id}/archive`)
export const annualDelete = id => request.delete(`/budget/annual/${id}`)
/** 年度调整（B2）：{ adjustType, amount, reason } → 调整单ID */
export const annualAdjust = (id, form) => request.post(`/budget/annual/${id}/adjust`, form || {})
/** 月度分解列表（含执行/余额/超预算标记） */
export const monthlyList = budgetId => request.get(`/budget/annual/${budgetId}/months`)
/** 新增/更新某月预算（B1）：{ budgetId, month, monthAmount } */
export const monthUpsert = data => request.put('/budget/month', data)
/** 月度调整（B1）：{ adjustType, amount, reason } → 调整单ID */
export const monthAdjust = (id, form) => request.post(`/budget/month/${id}/adjust`, form || {})
/** 跨月调剂：{ fromMonth, toMonth, amount, reason } */
export const monthReallocate = (budgetId, form) => request.post(`/budget/annual/${budgetId}/reallocate`, form || {})
/** 一键分解：{ mode: EVEN/RATIO, ratios } → 生成月数 */
export const monthGenerate = (budgetId, form) => request.post(`/budget/annual/${budgetId}/months/generate`, form || {})

// ================= 调整留痕 /api/budget/adjusts =================
export const adjustPage = params => request.get('/budget/adjusts/page', { params })

// ================= 执行分析 /api/budget/exec =================
/** 年度总览：{ year, cost, expense, total, byAccount } */
export const execOverview = params => request.get('/budget/exec/overview', { params })
/** 月度趋势：{ year, months:[{ month, costBudget, costActual, expenseBudget, expenseActual }] } */
export const execTrend = params => request.get('/budget/exec/trend', { params })
/** 管控链（B5）：{ year, month, annual, monthly, fund:{ cost, expense, total }, over } */
export const execChain = params => request.get('/budget/exec/chain', { params })
/** 月度资金计划执行：{ year, months:[{ month, costPlan, costActual, costRate, expensePlan, expenseActual, expenseRate }], total } */
export const execFundMonths = params => request.get('/budget/exec/fund-months', { params })
