import request from './request'

// ================= 投资测算 =================

// 测算版本分页（projectId/stage/status）
export const calcPage = params => request.get('/invest/calc/page', { params })

export const calcDetail = id => request.get(`/invest/calc/${id}`)

// 新建测算版本（versionCode 自动生成，净利率自动计算）
export const calcCreate = data => request.post('/invest/calc', data)

export const calcUpdate = (id, data) => request.put(`/invest/calc/${id}`, data)

export const calcDelete = id => request.delete(`/invest/calc/${id}`)

// 锁定版本（后评估基准，锁定后禁改）
export const calcLock = id => request.put(`/invest/calc/${id}/lock`)

// 后评估：拿地测算 vs 当前动态
export const calcPostEval = id => request.get(`/invest/calc/${id}/post-eval`)

// 重新计算 IRR + 动态回收期（基于月度现金流）并落库
export const recalcCalc = id => request.post(`/invest/calc/${id}/recalc`)

// ================= 货值台账 =================

// 货值分页（projectId/productCategory）
export const valuePage = params => request.get('/invest/value/page', { params })

export const valueCreate = data => request.post('/invest/value', data)

export const valueUpdate = (id, data) => request.put(`/invest/value/${id}`, data)

export const valueDelete = id => request.delete(`/invest/value/${id}`)

// 供销存：期初存货+新供货-已售+退房
export const valueStock = projectId =>
  request.get('/invest/value/stock', { params: { projectId } })

// ================= 动态利润 =================

// 最新动态利润 + 拿地基线对比
export const profitLatest = projectId =>
  request.get('/invest/profit', { params: { projectId } })

// 刷新动态利润快照（同月幂等）
export const profitRefresh = (projectId, month) =>
  request.post('/invest/profit/refresh', null, { params: { projectId, month } })

// ================= 投资总览 / 驾驶舱 =================

// 投资总览：KPI + 项目投资清单 + 状态/城市分布
export const investOverview = () => request.get('/invest/overview')

// 投资驾驶舱：KPI + 基线vs动态对比 + IRR排名 + 趋势 + 业态构成 + 预警
export const investCockpit = () => request.get('/invest/cockpit')

// ================= 公共 =================

// 项目下拉选项（id/projectCode/name/status）
export const projectOptions = () => request.get('/mdm/projects/options')
