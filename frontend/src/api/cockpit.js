import request from './request'

/**
 * 三级驾驶舱 API（严格对齐后端 /api/cockpit/** 与 /api/report/metrics 真实端点）
 * 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data；
 * code!==0 时 request.js 会 ElMessage.error(msg) 并 reject，前端只需不吞错即可展示后端提示。
 */

// ================= 一期经营总览（保留） =================
/** 经营卡片 + 项目红黄绿灯 + 近6个月趋势 */
export const cockpitOverview = () => request.get('/cockpit/overview')

// ================= 集团驾驶舱 =================
/** cards / projectMap / regionRanking / investmentRealization / riskAlerts */
export const cockpitGroup = () => request.get('/cockpit/group')

// ================= 区域驾驶舱 =================
/** orgId 必填：projectMatrix / threeCalc / fee / procurement */
export const cockpitRegion = orgId => request.get(`/cockpit/region/${orgId}`)

// ================= 项目驾驶舱 =================
/** projectId 必填：project / timeline / valueWaterfall / dynamicProfit / threeCalc / alertDistribution / ledger */
export const cockpitProject = projectId => request.get(`/cockpit/project/${projectId}`)

// ================= 专题分析 =================
/** topic: value / cost / sales / proc / expense → { topic, data } */
export const cockpitTopic = topic => request.get(`/cockpit/topics/${topic}`)

// ================= 下钻（汇总→明细） =================
/** metricCode 必填；orgId/projectId/month 可选。→ { metric, count, rows } */
export const cockpitDrill = (metricCode, params) =>
  request.get(`/cockpit/drill/${metricCode}`, { params })

// ================= 指标字典 /api/report/metrics =================
/** 分页列表：{ records, total, current, size } */
export const metricPage = params => request.get('/report/metrics/page', { params })
/** 全量列表：List */
export const metricList = params => request.get('/report/metrics', { params })
/** 详情 */
export const metricDetail = id => request.get(`/report/metrics/${id}`)
/** 新增 → id */
export const metricCreate = data => request.post('/report/metrics', data)
/** 修改 */
export const metricUpdate = (id, data) => request.put(`/report/metrics/${id}`, data)
/** 删除 */
export const metricDelete = id => request.delete(`/report/metrics/${id}`)
