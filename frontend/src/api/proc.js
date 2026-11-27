import request from './request'
import { ElMessage } from 'element-plus'

// =====================================================
// 招采管理 API（已对齐后端 /api/proc/** 真实端点）
// 后端未开放的操作统一走 notSupported 占位，避免误报成功
// =====================================================

/** 后端暂未开放的操作：提示 + 抛出，阻断后续“成功”反馈 */
const notSupported = label => async () => {
  ElMessage.warning(`「${label}」后端暂未开放该操作`)
  throw new Error(`${label}: not supported by backend`)
}

// ================= 主数据选项（复用 MDM 已有接口） =================
export const fetchProjectOptions = () => request.get('/mdm/projects/options')
export const fetchSupplierOptions = () => request.get('/mdm/suppliers/qualified-options')
export const fetchPurchaseCategories = () => request.get('/mdm/categories/tree', { params: { type: 'PURCHASE' } })
export const fetchCostAccounts = () => request.get('/mdm/accounts/tree')

// ================= 状态与字典 =================
/** 单据状态 → [中文名, el-tag type] */
export const STATUS_META = {
  DRAFT: ['草稿', 'info'],
  APPROVING: ['审批中', 'warning'],
  APPROVED: ['已通过', 'success'],
  REJECTED: ['已驳回', 'danger'],
  MERGED: ['已并入计划', 'info'],
  CONFIRMED: ['已确认', 'success'],
  PENDING: ['待提交', 'info'],
  VOIDED: ['已作废', 'info'],
  CLOSED: ['已关闭', 'info'],
  // 招采立项流程状态
  ENTRY: ['入围中', ''],
  TENDERING: ['发标中', ''],
  EVALUATING: ['评标中', ''],
  AWARDED: ['已定标', 'success'],
  // 发标
  PUBLISHED: ['发标中', ''],
  CLARIFY: ['答疑中', 'warning'],
  OPENED: ['已开标', 'success'],
  // 回标
  SUBMITTED: ['已回标', ''],
  WINNER: ['中标', 'success'],
  LOSER: ['未中标', 'info'],
  INVALID: ['废标', 'danger'],
  // 评标
  SCORING: ['评分中', 'warning'],
  REPORTED: ['报告已出', 'success'],
  // 战略集采
  ACTIVE: ['生效中', 'success'],
  EXPIRED: ['已过期', 'info'],
  ORDERED: ['已下单', ''],
  DELIVERED: ['已到货', 'success'],
  // 资格预审 / 符合性 / 考察结论 / 余量校验
  PASSED: ['通过', 'success'],
  FAILED: ['未通过', 'danger'],
  GOOD: ['优秀', 'success'],
  PASS: ['合格', ''],
  BAD: ['不合格', 'danger'],
  WARN: ['超余量预警', 'warning'],
  BLOCKED: ['阻断', 'danger'],
  // 通知
  UNSENT: ['未发送', 'info'],
  SENT: ['已发送', 'success'],
  // 紧急程度
  NORMAL: ['普通', 'info'],
  URGENT: ['紧急', 'danger']
}
/** 取状态元信息，overrides 可按单据覆盖同名状态文案，如 { APPROVED: ['已立项','success'] } */
export const statusMeta = (s, overrides) => {
  const m = (overrides && overrides[s]) || STATUS_META[s] || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}

/** 采购方式 */
export const PROC_METHODS = [
  { value: 'PUBLIC_BID', label: '公开招标' },
  { value: 'INVITE_BID', label: '邀请招标' },
  { value: 'COMPETITIVE', label: '竞争性谈判' },
  { value: 'INQUIRY', label: '询比价' },
  { value: 'DIRECT', label: '直接委托' },
  { value: 'FRAMEWORK', label: '战略集采' }
]
export const methodLabel = v => PROC_METHODS.find(m => m.value === v)?.label || v || '-'

/** 评标办法 */
export const EVAL_METHODS = [
  { value: 'COMPREHENSIVE', label: '综合评估法' },
  { value: 'LOWEST', label: '最低价法' },
  { value: 'COST_PERF', label: '性价比法' }
]
export const evalMethodLabel = v => EVAL_METHODS.find(m => m.value === v)?.label || v || '-'

/** 直接委托理由 */
export const DIRECT_REASONS = ['垄断', '应急', '续约', '其他']

/** 审批任务状态 → [中文名, el-tag type]（工作流 WflTask） */
export const TASK_META = {
  PENDING: ['待处理', 'warning'],
  APPROVED: ['已同意', 'success'],
  REJECTED: ['已驳回', 'danger'],
  TRANSFERRED: ['已转办', 'info'],
  CANCELLED: ['已作废', 'info']
}

/**
 * 审批轨迹：复用工作流模块已有接口
 * 先按 bizType 查实例列表定位 bizId 对应实例，再取实例任务时间线
 */
export const fetchApprovalTrail = async (bizType, bizId) => {
  const page = await request.get('/workflow/instances/page', { params: { bizType, size: 100 } })
  const inst = (page?.records || []).find(r => String(r.bizId) === String(bizId))
  if (!inst) return { instance: null, tasks: [] }
  const detail = await request.get(`/workflow/instances/${inst.id}`)
  return { instance: detail?.instance || null, tasks: detail?.tasks || [] }
}

// ================= 采购需求 /proc/requirements =================
export const reqPage = params => request.get('/proc/requirements/page', { params })
export const reqDetail = id => request.get(`/proc/requirements/${id}`)
export const reqCreate = data => request.post('/proc/requirements', data)
export const reqUpdate = data => request.put(`/proc/requirements/${data?.id}`, data)
/** 提交审批（后端 WorkflowClient.start(PROC_REQUIREMENT)），DRAFT→APPROVING */
export const reqSubmit = id => request.post(`/proc/requirements/${id}/submit`)
/** 作废：后端无对应端点 */
export const reqVoid = notSupported('需求作废')

// ================= 采购计划 /proc/plans =================
export const planPage = params => request.get('/proc/plans/page', { params })
/** 计划详情：主表 + /items 明细 */
export const planDetail = async id => {
  const plan = await request.get(`/proc/plans/${id}`).catch(() => null)
  if (!plan) return null
  const items = await request.get(`/proc/plans/${id}/items`).catch(() => [])
  return { ...plan, items: items || [] }
}
/** 新建：先建主表，再把已审批需求并入为计划明细 */
export const planSave = async data => {
  const { items, ...header } = data || {}
  const id = await request.post('/proc/plans', header)
  const requirementIds = (items || []).map(i => i.requirementId).filter(Boolean)
  if (requirementIds.length) await request.post(`/proc/plans/${id}/merge-requirements`, { requirementIds })
  return id
}
/** 编辑：仅更新主表字段（明细由后端 merge 维护） */
export const planUpdate = data => {
  const { id, items, ...header } = data || {}
  return request.put(`/proc/plans/${id}`, header)
}
/** 计划确认定稿 DRAFT→CONFIRMED */
export const planConfirm = id => request.put(`/proc/plans/${id}/confirm`)
/** 作废：后端无对应端点 */
export const planVoid = notSupported('计划作废')

// ================= 招采立项 /proc/projects =================
export const projPage = params => request.get('/proc/projects/page', { params })
export const projDetail = id => request.get(`/proc/projects/${id}`)
export const projCreate = data => request.post('/proc/projects', data)
export const projUpdate = data => request.put(`/proc/projects/${data?.id}`, data)
/** 提交立项审批（PROC_PROJECT），DRAFT→APPROVING→APPROVED */
export const projSubmit = id => request.post(`/proc/projects/${id}/submit`)
/** 流程状态推进：后端无对应端点（由入围/发标/评标/定标动作联动推进） */
export const projChangeStatus = notSupported('立项状态推进')
/** 作废：后端无对应端点 */
export const projVoid = notSupported('立项作废')

// ================= 入围与考察 /proc/entries =================
/** 后端仅支持按 procProjectId 列名单（无分页），前端分页 + 本地筛选 */
export const entryPage = async params => {
  const procProjectId = params?.procProjectId
  if (!procProjectId) return { records: [], total: 0 }
  let records = await request.get('/proc/entries', { params: { procProjectId } }).catch(() => [])
  records = records || []
  if (params?.precheck) records = records.filter(e => e.precheckResult === params.precheck)
  if (params?.conclusion) records = records.filter(e => e.inspectionConclusion === params.conclusion)
  const size = Number(params?.size) || records.length || 1
  const start = ((Number(params?.current) || 1) - 1) * size
  return { records: records.slice(start, start + size), total: records.length }
}
/** 单条详情：后端无对应端点，视图以 row 兜底 */
export const entryDetail = async () => null
export const entryCreate = data => request.post('/proc/entries', data)
export const entryUpdate = data => request.put(`/proc/entries/${data?.id}`, data)
/** 考察登记：复用入围更新（资格预审/评分/结论） */
export const entryInspect = (id, data) => request.put(`/proc/entries/${id}`, data)
/** 入围名单提交审批（bizId=procProjectId） */
export const entrySubmit = procProjectId => request.post(`/proc/entries/${procProjectId}/submit`)
export const entryDelete = id => request.delete(`/proc/entries/${id}`)

// ================= 发标答疑 /proc/tenders =================
/** clarifyNotes 存为 JSON 字符串 [{question,answer,time}]，解析并把 time 映射为 clarifyAt */
const parseClarifyNotes = v => {
  if (!v) return []
  let arr = v
  if (typeof v === 'string') {
    try { arr = JSON.parse(v) } catch (e) { return [] }
  }
  if (!Array.isArray(arr)) return []
  return arr.map(n => ({ ...n, clarifyAt: n.time || n.clarifyAt }))
}
export const tenderPage = async params => {
  const page = await request.get('/proc/tenders/page', { params }).catch(() => null)
  const records = (page?.records || []).map(t => ({ ...t, clarifyNotes: parseClarifyNotes(t.clarifyNotes) }))
  return { ...(page || {}), records }
}
/** 发标详情（含解析后的 clarifyNotes 答疑纪要） */
export const tenderDetail = async id => {
  const tender = await request.get(`/proc/tenders/${id}`).catch(() => null)
  if (!tender) return null
  return { ...tender, clarifyNotes: parseClarifyNotes(tender.clarifyNotes) }
}
export const tenderCreate = data => request.post('/proc/tenders', data)
/** 编辑：后端无对应端点 */
export const tenderUpdate = notSupported('发标编辑')
/** 发布：后端无对应端点（发标即 create 时置 PUBLISHED） */
export const tenderPublish = notSupported('发标发布')
/** 开标 PUBLISHED/CLARIFY→OPENED */
export const tenderOpen = id => request.put(`/proc/tenders/${id}/open`)
/** 关闭标段 →CLOSED */
export const tenderClose = id => request.put(`/proc/tenders/${id}/close`)
/** 退还保证金 */
export const tenderRefund = id => request.put(`/proc/tenders/${id}/refund-deposit`)
/** 新增答疑纪要 { question, answer }，状态→CLARIFY */
export const tenderClarify = (id, data) => request.put(`/proc/tenders/${id}/clarify`, data)

// ================= 回标管理 /proc/bids =================
/** 后端仅支持按 tenderId 列回标（无分页），先取立项下发标再聚合 */
const aggregateBids = async procProjectId => {
  const tenderPage = await request.get('/proc/tenders/page', { params: { current: 1, size: 200, procProjectId } }).catch(() => null)
  const tenders = tenderPage?.records || []
  const lists = await Promise.all(tenders.map(t => request.get('/proc/bids', { params: { tenderId: t.id } }).catch(() => [])))
  return lists.flat()
}
/** 按立项维度聚合回标，前端分页 + 本地筛选（bidStatus / compliance） */
export const bidPage = async params => {
  const procProjectId = params?.procProjectId
  if (!procProjectId) return { records: [], total: 0 }
  let records = await aggregateBids(procProjectId)
  if (params?.bidStatus) records = records.filter(b => b.bidStatus === params.bidStatus)
  if (params?.compliance) records = records.filter(b => b.complianceResult === params.compliance)
  const size = Number(params?.size) || records.length || 1
  const start = ((Number(params?.current) || 1) - 1) * size
  return { records: records.slice(start, start + size), total: records.length }
}
/** 单条详情：后端无对应端点，视图以 row 兜底 */
export const bidDetail = async () => null
/** 回标登记（线下回标录入） */
export const bidCreate = data => request.post('/proc/bids', data)
/** 编辑：后端无对应端点 */
export const bidUpdate = notSupported('回标编辑')
/** 废标：后端无对应端点 */
export const bidInvalid = notSupported('回标废标')
/** 恢复：后端无对应端点 */
export const bidRestore = notSupported('回标恢复')

// ================= 评标管理 /proc/evaluations =================
/** 后端仅支持按 procProjectId 列评标（无分页），前端包装分页 */
export const evalPage = async params => {
  const procProjectId = params?.procProjectId
  if (!procProjectId) return { records: [], total: 0 }
  const records = await request.get('/proc/evaluations', { params: { procProjectId } }).catch(() => [])
  return { records: records || [], total: (records || []).length }
}
/** 评标详情：主表 + /details 评分明细 */
export const evalDetail = async id => {
  const evaluation = await request.get(`/proc/evaluations/${id}`).catch(() => null)
  if (!evaluation) return null
  const details = await request.get(`/proc/evaluations/${id}/details`).catch(() => [])
  return { ...evaluation, details: details || [] }
}
export const evalCreate = data => request.post('/proc/evaluations', data)
/** 编辑：后端无对应端点 */
export const evalUpdate = notSupported('评标编辑')
/** 评分明细逐行保存：每行一次 /score（{bidId,expertName,techScore,bizScore}），后端回写均值 */
export const evalSaveDetails = async (id, details) => {
  for (const d of details || []) {
    await request.post(`/proc/evaluations/${id}/score`, {
      bidId: d.bidId, expertName: d.expertName, techScore: d.techScore, bizScore: d.bizScore
    })
  }
}
/** 生成评标报告：后端 /finish 自动排名并生成结论（SCORING→REPORTED） */
export const evalReport = id => request.put(`/proc/evaluations/${id}/finish`)
/** 删除：后端无对应端点 */
export const evalDelete = notSupported('评标删除')

// ================= 定标管理 /proc/awards =================
export const awardPage = params => request.get('/proc/awards/page', { params })
export const awardDetail = id => request.get(`/proc/awards/${id}`)
export const awardCreate = data => request.post('/proc/awards', data)
/** 编辑：后端无对应端点 */
export const awardUpdate = notSupported('定标编辑')
/** 提交定标审批（PROC_AWARD），DRAFT→APPROVING→APPROVED（通过后招采立项→AWARDED） */
export const awardSubmit = id => request.post(`/proc/awards/${id}/submit`)
/** 发送中标通知书：后端无对应端点 */
export const awardSendNotice = notSupported('发送中标通知')
/** 生成合同草稿：后端无对应端点 */
export const awardGenerateContract = notSupported('生成合同')
/** 删除：后端无对应端点 */
export const awardDelete = notSupported('定标删除')

// ================= 直接委托 /proc/directs =================
export const directPage = params => request.get('/proc/directs/page', { params })
export const directDetail = id => request.get(`/proc/directs/${id}`)
export const directCreate = data => request.post('/proc/directs', data)
export const directUpdate = data => request.put(`/proc/directs/${data?.id}`, data)
/** 提交审批（PROC_DIRECT，金额分级路由） */
export const directSubmit = id => request.post(`/proc/directs/${id}/submit`)
/** 作废：后端无对应端点 */
export const directVoid = notSupported('委托作废')

// ================= 战略集采 /proc/frameworks =================
export const fwPage = params => request.get('/proc/frameworks/page', { params })
/** 协议详情（unitPriceTable 在实体上；orders 后端无查询端点，视图兜底为空） */
export const fwDetail = id => request.get(`/proc/frameworks/${id}`)
export const fwCreate = data => request.post('/proc/frameworks', data)
export const fwUpdate = data => request.put(`/proc/frameworks/${data?.id}`, data)
export const fwDelete = id => request.delete(`/proc/frameworks/${id}`)
/** 集采下单：{ frameworkId, projectId, content, quantity, amount, orderDate } */
export const fwOrderCreate = data => {
  const { frameworkId, ...order } = data || {}
  return request.post(`/proc/frameworks/${frameworkId}/orders`, order)
}
/** 订单状态推进：后端无对应端点 */
export const fwOrderStatus = notSupported('集采订单状态')

// ================= 零星采购 /proc/misc =================
export const fetchMisc = params => request.get('/proc/misc/page', { params })
export const fetchMiscDetail = id => request.get(`/proc/misc/${id}`)
export const createMisc = data => request.post('/proc/misc', data)
export const updateMisc = data => request.put(`/proc/misc/${data?.id}`, data)
export const deleteMisc = id => request.delete(`/proc/misc/${id}`)
/** 提交审批（后端硬校验比价家数≥3且报价一致），DRAFT→APPROVING */
export const submitMisc = id => request.post(`/proc/misc/${id}/submit`)
/** 比价子表 */
export const fetchMiscCompares = id => request.get(`/proc/misc/${id}/compares`)
export const addMiscCompare = (id, data) => request.post(`/proc/misc/${id}/compares`, data)
export const deleteMiscCompare = (id, compareId) => request.delete(`/proc/misc/${id}/compares/${compareId}`)

// ================= 评标专家库 /proc/experts =================
export const fetchExperts = params => request.get('/proc/experts/page', { params })
export const fetchExpertList = params => request.get('/proc/experts', { params })
export const createExpert = data => request.post('/proc/experts', data)
export const updateExpert = data => request.put(`/proc/experts/${data?.id}`, data)
export const deleteExpert = id => request.delete(`/proc/experts/${id}`)
/** 抽取：{ procProjectId, count, avoidKeyword } 随机取 N 名（回避关键词过滤） */
export const extractExperts = data => request.post('/proc/experts/extract', data)

// ================= 采购台账 /proc/ledger =================
/** 全流程台账（立项维度 JOIN 定标） */
export const ledgerPage = params => request.get('/proc/ledger/page', { params })
/** 招采过程总览：9 节点完成标记（需求→…→合同），行含 reqDone/planDone/…/contractDone */
export const overviewPage = params => request.get('/proc/ledger/overview', { params })
// ================= 招采驾驶舱 /proc/cockpit =================
/** 指标总览 + 统计分析（方式/阶段/周期/偏差/供应商）+ 预警（围串标/超预算/超期） */
export const procCockpit = () => request.get('/proc/cockpit')
/** 采购统计：projectId 必填，未传则返回空 */
export const ledgerStats = projectId => {
  if (!projectId) return Promise.resolve({})
  return request.get('/proc/ledger/stats', { params: { projectId } }).catch(() => ({}))
}
/** 单标段全链路：按立项聚合 项目/入围/发标/回标/评标/定标（需求/计划/合同无对应端点置空） */
export const ledgerDetail = async procProjectId => {
  if (!procProjectId) return null
  const project = await request.get(`/proc/projects/${procProjectId}`).catch(() => null)
  const entries = await request.get('/proc/entries', { params: { procProjectId } }).catch(() => [])
  const tenderPage = await request.get('/proc/tenders/page', { params: { current: 1, size: 1, procProjectId } }).catch(() => null)
  const tender = (tenderPage?.records || [])[0] || null
  const bids = tender ? await request.get('/proc/bids', { params: { tenderId: tender.id } }).catch(() => []) : []
  const evaluations = await request.get('/proc/evaluations', { params: { procProjectId } }).catch(() => [])
  const evaluation = evaluations[0] || null
  const awardPage = await request.get('/proc/awards/page', { params: { current: 1, size: 1, procProjectId } }).catch(() => null)
  const award = (awardPage?.records || [])[0] || null
  return {
    project: project || null,
    entries: entries || [],
    tender,
    bids: bids || [],
    evaluation,
    award,
    requirement: null,
    plan: null,
    contract: null
  }
}
