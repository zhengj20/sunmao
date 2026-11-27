import request from './request'

// ================= 资金计划 =================

// 按月查询：各项目收付计划 + 汇总 + 大额付款标记
export const monthPlan = month => request.get('/fund/plan', { params: { month } })

// 自动汇总生成月度计划（幂等：覆盖 DRAFT，不动 FINAL）
export const generatePlan = month =>
  request.post('/fund/plan/generate', null, { params: { month } })

// 计划定稿
export const finalizePlan = id => request.put(`/fund/plan/${id}/finalize`)

// ================= 收付执行 =================

// 计划 vs 实际对比（含执行率）
export const executeMonth = month => request.get('/fund/execute', { params: { month } })

// 资金日报：当日实付清单 + 汇总
export const dailyReport = date => request.get('/fund/execute/daily', { params: { date } })

// ================= 融资台账 =================

// 融资分页（projectId/financeType/status）
export const financePage = params => request.get('/fund/finance/page', { params })

export const financeDetail = id => request.get(`/fund/finance/${id}`)

export const financeCreate = data => request.post('/fund/finance', data)

export const financeUpdate = (id, data) => request.put(`/fund/finance/${id}`, data)

export const financeDelete = id => request.delete(`/fund/finance/${id}`)

// 到期提醒（默认90天内）
export const expiringFinances = (days = 90) =>
  request.get('/fund/finance/expiring', { params: { days } })

// ================= 公共 =================

export const projectOptions = () => request.get('/mdm/projects/options')
