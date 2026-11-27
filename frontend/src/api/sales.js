import request from './request'

// =====================================================
// 销售管理 API（严格对齐后端 /api/sales/** 真实端点）
// 后端：SaleRoom/SalePrice/SaleDiscount/SaleSubscription/SaleContract/SalePayment/SaleRefund/SaleReport
// 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data；
// code!==0 时 request.js 会 ElMessage.error(msg) 并 reject —— 前端只需不吞错即可展示后端提示（如签约 R3 折扣刚性）
// =====================================================

// ================= 公共选项（复用 MDM 已有接口） =================
export const fetchProjectOptions = () => request.get('/mdm/projects/options')
/** 客户下拉：复用 MDM 客户分页（id/name） */
export const fetchCustomerOptions = async (kw = '') => {
  const page = await request.get('/mdm/customers/page', { params: { current: 1, size: 200, kw } }).catch(() => null)
  return page?.records || []
}
/** 房源下拉：复用销控分页（取 roomId/roomCode/status） */
export const fetchRoomOptions = async (projectId, status) => {
  const page = await request.get('/sales/rooms/page', { params: { current: 1, size: 500, projectId, status } }).catch(() => null)
  return page?.records || []
}

// ================= 状态与字典 =================
/** 房源销控状态 → [中文名, el-tag type] */
export const ROOM_STATUS = {
  SELLABLE: ['可售', 'success'],
  LOCKED: ['锁定', 'warning'],
  SMALL_DEPOSIT: ['小定', ''],
  BIG_DEPOSIT: ['大定', ''],
  SUBSCRIBED: ['已认购', 'primary'],
  CONTRACTED: ['已签约', 'danger'],
  FILED: ['已网签', ''],
  REFUNDED: ['已退房', 'info']
}
/** 认购状态 */
export const SUB_STATUS = {
  DRAFT: ['草稿', 'info'],
  APPROVING: ['审批中', 'warning'],
  ACTIVE: ['生效', 'success'],
  CONVERTED: ['已转签约', 'primary'],
  REJECTED: ['已驳回', 'danger'],
  TERMINATED: ['已退定', 'info']
}
/** 签约状态 */
export const CONTRACT_STATUS = {
  DRAFT: ['草稿', 'info'],
  APPROVING: ['审批中', 'warning'],
  ACTIVE: ['生效', 'success'],
  FILED: ['已网签', 'primary'],
  REFUNDED: ['已退房', 'danger']
}
/** 退换状态 */
export const REFUND_STATUS = {
  DRAFT: ['草稿', 'info'],
  APPROVING: ['审批中', 'warning'],
  APPROVED: ['已通过', 'success'],
  REJECTED: ['已驳回', 'danger']
}
/** 应收计划状态 */
export const PLAN_STATUS = {
  DUE: ['待收', 'warning'],
  RECEIVED: ['已收', 'success'],
  OVERDUE: ['逾期', 'danger']
}
/** 按揭阶段 */
export const MORTGAGE_STAGE = {
  APPLY: ['申请', 'info'],
  APPROVE: ['审批', 'warning'],
  MORTGAGE: ['抵押', 'primary'],
  DISBURSE: ['放款', 'success']
}
/** 折扣级别 */
export const DISCOUNT_LEVEL = {
  AGENT: ['置业顾问', 'info'],
  MGR: ['经理', 'warning'],
  GM: ['总经理', 'success']
}
/** 折扣方案状态 */
export const DISCOUNT_STATUS = {
  ACTIVE: ['生效', 'success'],
  INACTIVE: ['停用', 'info']
}
/** 付款方式 */
export const PAY_METHODS = [
  { value: 'ONE_TIME', label: '一次性' },
  { value: 'MORTGAGE', label: '按揭' },
  { value: 'INSTALLMENT', label: '分期' }
]
export const payMethodLabel = v => PAY_METHODS.find(m => m.value === v)?.label || v || '-'
/** 退换类型 */
export const REFUND_TYPE = { REFUND: ['退定/退房', ''], EXCHANGE: ['换房', ''] }
/** 实收方式 */
export const PAY_WAYS = [
  { value: 'POS', label: 'POS刷卡' },
  { value: 'TRANSFER', label: '转账' },
  { value: 'CASH', label: '现金' }
]
export const payWayLabel = v => PAY_WAYS.find(m => m.value === v)?.label || v || '-'

/** 取 [中文名, tagType]，兜底 [原值, 'info'] */
export const statusMeta = (s, map) => {
  const m = (map && map[s]) || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}

// ================= 房源销控 /sales/rooms =================
export const roomPage = params => request.get('/sales/rooms/page', { params })
export const roomDetail = roomId => request.get(`/sales/rooms/${roomId}`)
/** 从 MDM 房间初始化销控（幂等）→ 返回新增条数 */
export const roomInit = projectId => request.post('/sales/rooms/init', null, { params: { projectId } })
/** 锁定（默认24小时到期） */
export const roomLock = (roomId, expire) => request.post(`/sales/rooms/${roomId}/lock`, expire ? { expire } : {})
/** 放开 */
export const roomUnlock = roomId => request.post(`/sales/rooms/${roomId}/unlock`)

// ================= 一房一价 /sales/prices =================
/** 修改房源表价/底价 { tablePrice, floorPrice } */
export const updateRoomPrice = (roomId, data) => request.put(`/sales/prices/rooms/${roomId}`, data)

// ================= 折扣方案 /sales/discounts =================
export const discountPage = params => request.get('/sales/discounts/page', { params })
export const discountDetail = id => request.get(`/sales/discounts/${id}`)
export const discountCreate = data => request.post('/sales/discounts', data)
export const discountUpdate = data => request.put('/sales/discounts', data)
export const discountDelete = id => request.delete(`/sales/discounts/${id}`)

// ================= 认购管理 /sales/subscriptions =================
export const subPage = params => request.get('/sales/subscriptions/page', { params })
export const subDetail = id => request.get(`/sales/subscriptions/${id}`)
/** 登记认购（DRAFT，自动预占房源） */
export const subCreate = data => request.post('/sales/subscriptions', data)
/** 提交审批（SALE_SUBSCRIPTION） */
export const subSubmit = id => request.post(`/sales/subscriptions/${id}/submit`)
/** 转签约：{ contractPrice, payMethod, signedDate } → 返回合同ID */
export const subConvert = (id, form) => request.post(`/sales/subscriptions/${id}/convert`, form || {})
/** 退定：{ reason } → 返回退换单ID（自动发起退定审批） */
export const subRefund = (id, reason) => request.post(`/sales/subscriptions/${id}/refund`, { reason })
/** 超期未签约预警标记 → 返回命中条数 */
export const subWarnOverdue = () => request.post('/sales/subscriptions/warn-overdue')

// ================= 签约管理 /sales/contracts =================
export const contractPage = params => request.get('/sales/contracts/page', { params })
export const contractDetail = id => request.get(`/sales/contracts/${id}`)
/** 签约登记（DRAFT，自动算折扣率） */
export const contractCreate = data => request.post('/sales/contracts', data)
/** 提交审批（R3 折扣刚性校验 + SALE_CONTRACT）—— 超权限折扣后端会 R3 报错 */
export const contractSubmit = id => request.post(`/sales/contracts/${id}/submit`)
/** 发起特价房审批（超权限折扣前置，SALE_SPECIAL_PRICE） */
export const contractSpecialPrice = id => request.post(`/sales/contracts/${id}/special-price`)
/** 退房：{ reason } → 返回退换单ID（自动发起退房审批） */
export const contractRefund = (id, reason) => request.post(`/sales/contracts/${id}/refund`, { reason })

// ================= 回款管理 /sales/payments =================
export const planPage = params => request.get('/sales/payments/plans/page', { params })
/** 按合同查应收计划（List） */
export const plansByContract = contractId => request.get(`/sales/payments/plans/${contractId}`)
/** 逾期清单（自动标记 OVERDUE）→ List */
export const overduePlans = projectId => request.get('/sales/payments/overdue', { params: { projectId } })
export const receiptPage = params => request.get('/sales/payments/receipts/page', { params })
/** 实收登记（核销应收 R5）：POST /receipts/{contractId} { planId, amount, method, ticketNo, receivedDate } */
export const receive = (contractId, form) => request.post(`/sales/payments/receipts/${contractId}`, form)
export const mortgagePage = params => request.get('/sales/payments/mortgages/page', { params })
export const mortgageCreate = data => request.post('/sales/payments/mortgages', data)
export const mortgageUpdate = data => request.put('/sales/payments/mortgages', data)

// ================= 退换管理 /sales/refunds =================
export const refundPage = params => request.get('/sales/refunds/page', { params })
export const refundDetail = id => request.get(`/sales/refunds/${id}`)
/** 登记退换单（DRAFT）：contractId(退房) 或 subscriptionId(退定) + reason */
export const refundCreate = data => request.post('/sales/refunds', data)
/** 提交审批（退房 SALE_REFUND / 退定 SALE_SUBSCRIPTION_TERMINATE） */
export const refundSubmit = id => request.post(`/sales/refunds/${id}/submit`)

// ================= 销售报表 /sales/reports =================
/** 日报：{ subscription:{count,amount}, contract:{count,amount}, receipt:{count,amount}, collectionRate } */
export const reportDaily = projectId => request.get('/sales/reports/daily', { params: { projectId } })
/** 去化率：{ totalRooms, soldRooms, totalValue, soldValue, turnoverRateValue, turnoverRateCount } */
export const reportTurnover = projectId => request.get('/sales/reports/turnover', { params: { projectId } })
/** 价格实现率：{ avgContractPrice, avgTablePrice, priceAchievementRate } */
export const reportPriceAchievement = projectId => request.get('/sales/reports/price-achievement', { params: { projectId } })

// =====================================================
// 销售扩展（对标明源）：客户跟进 / 销售目标 / 渠道 / 驾驶舱 / 总览 / 顾问业绩 / 蓄客漏斗
// =====================================================

// ================= 客户跟进 /sales/follow =================
/** 跟进记录分页 */
export const followPage = params => request.get('/sales/follow/page', { params })
/** 客户池概览 KPI { total, following, deal, lost, highIntent, overdue } */
export const followPool = projectId => request.get('/sales/follow/pool', { params: { projectId } })
/** 逾期跟进清单 → List */
export const followOverdue = projectId => request.get('/sales/follow/overdue', { params: { projectId } })
export const followDetail = id => request.get(`/sales/follow/${id}`)
export const followCreate = data => request.post('/sales/follow', data)
export const followUpdate = data => request.put('/sales/follow', data)
export const followDelete = id => request.delete(`/sales/follow/${id}`)
/** 跟进方式 */
export const FOLLOW_METHODS = ['电话', '到访', '微信', '短信']
/** 意向度 */
export const INTENT_LEVEL = { HIGH: ['高意向', 'danger'], MID: ['中意向', 'warning'], LOW: ['低意向', 'info'] }
/** 成交状态 */
export const DEAL_STATUS = { FOLLOWING: ['跟进中', 'primary'], DEAL: ['已成交', 'success'], LOST: ['已流失', 'info'] }

// ================= 销售目标 /sales/targets =================
export const targetPage = params => request.get('/sales/targets/page', { params })
export const targetDetail = id => request.get(`/sales/targets/${id}`)
export const targetCreate = data => request.post('/sales/targets', data)
export const targetUpdate = data => request.put('/sales/targets', data)
export const targetDelete = id => request.delete(`/sales/targets/${id}`)
/** 周期类型 */
export const TARGET_PERIOD = { MONTH: '月度', QUARTER: '季度', YEAR: '年度' }
/** 指标口径 */
export const TARGET_METRIC = { AMOUNT: '金额', COUNT: '套数', AREA: '面积' }

// ================= 渠道管理 /sales/channels =================
export const channelPage = params => request.get('/sales/channels/page', { params })
export const channelDetail = id => request.get(`/sales/channels/${id}`)
export const channelCreate = data => request.post('/sales/channels', data)
export const channelUpdate = data => request.put('/sales/channels', data)
export const channelDelete = id => request.delete(`/sales/channels/${id}`)
/** 渠道类型 */
export const CHANNEL_TYPES = ['中介', '分销', '线上', '老带新', '自然来访']
export const CHANNEL_STATUS = { ACTIVE: ['生效', 'success'], INACTIVE: ['停用', 'info'] }

// ================= 驾驶舱 / 总览 / 顾问 / 漏斗 / 渠道结构 =================
/** 销售驾驶舱 { kpi, contractTrend, receiptTrend, channel, layout, topAdvisors, funnel } */
export const dashboardCockpit = projectId => request.get('/sales/dashboard/cockpit', { params: { projectId } })
/** 销售总览 { progress, buildingBoard, layoutBoard, priceTrend } */
export const dashboardOverview = projectId => request.get('/sales/dashboard/overview', { params: { projectId } })
/** 置业顾问业绩 { total, list } */
export const dashboardAdvisor = projectId => request.get('/sales/dashboard/advisor', { params: { projectId } })
/** 蓄客漏斗 { stages, conversions } */
export const dashboardFunnel = projectId => request.get('/sales/dashboard/funnel', { params: { projectId } })
/** 渠道结构 { visitByChannel, contractBySupplier } */
export const dashboardChannel = projectId => request.get('/sales/dashboard/channel', { params: { projectId } })

// ================= 面积补差（多退少补）/sales/area-diff =================
export const areaDiffPage = params => request.get('/sales/area-diff/page', { params })
export const areaDiffStats = projectId => request.get('/sales/area-diff/stats', { params: { projectId } })
export const areaDiffCreate = data => request.post('/sales/area-diff', data)
/** 批量导入：POST /import { projectId, csv, remark }，csv 每行：房号,合同面积,实测面积,单价 */
export const areaDiffImport = data => request.post('/sales/area-diff/import', data)
/** 生成补差单（少补→应收计划；多退→待退款） */
export const areaDiffSettle = id => request.post(`/sales/area-diff/${id}/settle`)
/** 标记已结清（已收款/已退款） */
export const areaDiffSettleDone = (id, settled) => request.post(`/sales/area-diff/${id}/settle-done`, { settled })
export const areaDiffDelete = id => request.delete(`/sales/area-diff/${id}`)
export const AREA_DIFF_DIRECTION = { COLLECT: ['少补(收款)', 'danger'], REFUND: ['多退(退款)', 'success'], NONE: ['无差异', 'info'] }
export const AREA_DIFF_STATUS = { PENDING: ['待结算', 'warning'], GENERATED: ['已生成', 'primary'], SETTLED: ['已结清', 'success'] }

// ================= 来账（银企直联/手机短信）/sales/incoming =================
export const incomingPage = params => request.get('/sales/incoming/page', { params })
export const incomingStats = projectId => request.get('/sales/incoming/stats', { params: { projectId } })
export const incomingCreate = data => request.post('/sales/incoming', data)
/** 模拟接收银企直联/短信推送（演示） */
export const incomingSimulate = (projectId, source, max) => request.post('/sales/incoming/simulate', null, { params: { projectId, source, max } })
/** 智能匹配建议 { incoming, candidates, best } */
export const incomingSuggest = id => request.get(`/sales/incoming/${id}/suggest`)
/** 匹配（手动指定合同或自动取最佳）→ 核销生成实收 */
export const incomingMatch = (id, contractId) => request.post(`/sales/incoming/${id}/match`, contractId ? { contractId } : {})
export const incomingReject = id => request.post(`/sales/incoming/${id}/reject`)
export const INCOMING_SOURCE = { BANK: ['银企直联', 'primary'], SMS: ['手机短信', 'success'] }
export const INCOMING_STATUS = { PENDING: ['待匹配', 'warning'], MATCHED: ['已匹配', 'success'], REJECTED: ['已排除', 'info'] }
