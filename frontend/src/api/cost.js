import request from './request'

// ========== 公共引用（mdm 已有接口） ==========
export const fetchProjectOptions = () => request.get('/mdm/projects/options')
export const fetchAccountTree = params => request.get('/mdm/accounts/tree', { params })
export const fetchSupplierOptions = () => request.get('/mdm/suppliers/options')

// ========== 目标成本 ==========
export const fetchTargetVersions = async params => {
  const page = await request.get('/cost/targets/page', { params: { ...params, current: 1, size: 200 } })
  return page?.records || []
}
export const fetchTargetDetail = id => request.get(`/cost/targets/${id}`)
export const fetchTargetItems = versionId => request.get(`/cost/targets/${versionId}/items`)
export const createTargetVersion = data => request.post('/cost/targets', { version: data, items: [] })
export const updateTargetVersion = data => request.put('/cost/targets', { version: data })
export const submitTargetVersion = id => request.post(`/cost/targets/${id}/submit`)
export const saveTargetItems = async (versionId, items) => {
  for (const item of items) {
    await request.post(`/cost/targets/${versionId}/items`, item)
  }
}
export const exportTargetExcel = id => request.get(`/cost/targets/${id}/export`)
export const importTargetExcel = (id, rows) => request.post(`/cost/targets/${id}/import`, rows)

// ========== 合约规划 ==========
export const fetchPlans = params => request.get('/cost/plans/page', { params })
export const fetchPlanDetail = async id => {
  const [plan, items] = await Promise.all([
    request.get(`/cost/plans/${id}`),
    request.get(`/cost/plans/${id}/items`)
  ])
  return { ...plan, items: items || [] }
}
export const createPlan = data => request.post('/cost/plans', { plan: data, items: [] })
export const updatePlan = data => request.put('/cost/plans', { plan: data })
export const deletePlan = id => request.delete(`/cost/plans/${id}`)
export const savePlanItems = async (planId, items) => {
  for (const item of items) {
    await request.post(`/cost/plans/${planId}/items`, item)
  }
}
export const submitPlan = id => request.post(`/cost/plans/${id}/submit`)
export const submitPlanAdjustment = id => request.post(`/cost/plans/${id}/submit-adjustment`)

// ========== 合同管理 ==========
export const fetchContracts = params => request.get('/cost/contracts/page', { params })
export const fetchContractSummary = async params => {
  try {
    const [cPage, chPage, pPage] = await Promise.all([
      request.get('/cost/contracts/page', { params: { ...params, current: 1, size: 1000 } }),
      request.get('/cost/changes/page', { params: { ...params, current: 1, size: 1000 } }),
      request.get('/cost/payments/page', { params: { ...params, current: 1, size: 1000 } })
    ])
    const contractAmount = (cPage?.records || []).reduce((s, r) => s + (Number(r.signedAmount) || 0), 0)
    const changeAmount = (chPage?.records || []).reduce((s, r) => s + (Number(r.confirmedAmount) || 0), 0)
    const paidAmount = (pPage?.records || []).reduce((s, r) => s + (Number(r.paidAmount) || 0), 0)
    return { contractAmount, changeAmount, paidAmount, remainAmount: contractAmount + changeAmount - paidAmount }
  } catch {
    return { contractAmount: 0, changeAmount: 0, paidAmount: 0, remainAmount: 0 }
  }
}
export const fetchContractOptions = async params => {
  const page = await request.get('/cost/contracts/page', { params: { ...params, current: 1, size: 200 } })
  return page?.records || []
}
export const fetchContractDetail = id => request.get(`/cost/contracts/${id}`)
export const createContract = data => request.post('/cost/contracts', { contract: data })
export const updateContract = data => request.put('/cost/contracts', { contract: data })
export const deleteContract = id => request.delete(`/cost/contracts/${id}`)
export const fetchContractChanges = async contractId => {
  const page = await request.get('/cost/changes/page', { params: { contractId, current: 1, size: 200 } })
  return page?.records || []
}
export const fetchContractPayments = async contractId => {
  const page = await request.get('/cost/payments/page', { params: { contractId, current: 1, size: 200 } })
  return page?.records || []
}
export const fetchContractPaymentPlans = contractId => request.get(`/cost/contracts/${contractId}/payment-plans`)
export const addContractPaymentPlan = (contractId, data) => request.post(`/cost/contracts/${contractId}/payment-plans`, data)
export const updateContractPaymentPlan = data => request.put(`/cost/contracts/${data.contractId}/payment-plans`, data)
export const deleteContractPaymentPlan = (contractId, planId) => request.delete(`/cost/contracts/${contractId}/payment-plans/${planId}`)
export const fetchContractChangeRate = contractId => request.get(`/cost/contracts/${contractId}/change-rate`)
export const releaseRetention = (id, data) => request.put(`/cost/contracts/${id}/release-retention`, data)
export const settleContract = (id, data) => request.put(`/cost/contracts/${id}/settle`, data)

// ========== 变更管理 ==========
export const fetchChanges = params => request.get('/cost/changes/page', { params })
export const fetchChangeSummary = async params => {
  try {
    const page = await request.get('/cost/changes/page', { params: { ...params, current: 1, size: 1000 } })
    const records = page?.records || []
    return {
      count: records.length,
      estimateAmount: records.reduce((s, r) => s + (Number(r.estimateAmount) || 0), 0),
      confirmedAmount: records.reduce((s, r) => s + (Number(r.confirmedAmount) || 0), 0),
      deductAmount: records.reduce((s, r) => s + (Number(r.deductAmount) || 0), 0)
    }
  } catch {
    return { count: 0, estimateAmount: 0, confirmedAmount: 0, deductAmount: 0 }
  }
}
export const fetchChangeDetail = id => request.get(`/cost/changes/${id}`)
export const createChange = data => request.post('/cost/changes', data)
export const updateChange = data => request.put('/cost/changes', data)
export const deleteChange = id => request.delete(`/cost/changes/${id}`)
export const submitChange = id => request.post(`/cost/changes/${id}/submit-estimate`)
export const confirmChange = (id, data) => request.post(`/cost/changes/${id}/submit-confirm`, data)

// ========== 变更事项申请（签证/设计变更的上游，1事项→N变更） ==========
export const fetchMatters = params => request.get('/cost/changes/matters/page', { params })
export const fetchMatter = id => request.get(`/cost/changes/matters/${id}`)
export const fetchMatterSummary = id => request.get(`/cost/changes/matters/${id}/summary`)
export const fetchMatterChanges = id => request.get(`/cost/changes/matters/${id}/changes`)
export const createMatter = data => request.post('/cost/changes/matters', data)
export const updateMatter = data => request.put('/cost/changes/matters', data)
export const deleteMatter = id => request.delete(`/cost/changes/matters/${id}`)
export const approveMatter = id => request.post(`/cost/changes/matters/${id}/approve`)
export const closeMatter = id => request.post(`/cost/changes/matters/${id}/close`)

// ========== 付款管理 ==========
export const fetchPayments = params => request.get('/cost/payments/page', { params })
export const fetchPaymentSummary = async params => {
  try {
    const page = await request.get('/cost/payments/page', { params: { ...params, current: 1, size: 1000 } })
    const records = page?.records || []
    const applyAmount = records.reduce((s, r) => s + (Number(r.applyAmount) || 0), 0)
    const approvedAmount = records.reduce((s, r) => s + (Number(r.approvedAmount) || 0), 0)
    const paidAmount = records.reduce((s, r) => s + (Number(r.paidAmount) || 0), 0)
    return { applyAmount, approvedAmount, paidAmount, unpaidAmount: approvedAmount - paidAmount }
  } catch {
    return { applyAmount: 0, approvedAmount: 0, paidAmount: 0, unpaidAmount: 0 }
  }
}
export const fetchPaymentDetail = id => request.get(`/cost/payments/${id}`)
export const createPayment = data => request.post('/cost/payments', data)
export const updatePayment = data => request.put('/cost/payments', data)
export const deletePayment = id => request.delete(`/cost/payments/${id}`)
export const submitPayment = id => request.post(`/cost/payments/${id}/submit`)
export const registerPayment = (id, data) => request.put(`/cost/payments/${id}/mark-paid`, data)

// ========== 动态成本 ==========
export const fetchDynamicSnapshots = async params => {
  const data = await request.get('/cost/dynamic', { params })
  if (Array.isArray(data)) return data
  if (data?.history && Array.isArray(data.history)) return data.history
  return []
}
export const fetchDynamicDetail = async params => {
  const data = await request.get('/cost/dynamic/history', { params: { projectId: params.projectId } })
  if (Array.isArray(data)) {
    if (params.month) return data.find(s => s.snapMonth === params.month) || data[0] || null
    return data[0] || null
  }
  return data
}
export const fetchThreeWayCompare = projectId => request.get('/cost/dynamic/three-way-compare', { params: { projectId } })
export const fetchCostWarnings = projectId => request.get('/cost/dynamic/warning-center', { params: { projectId } })

// ========== 无效成本 ==========
export const fetchInvalids = params => request.get('/cost/invalids/page', { params })
export const fetchInvalidSummary = async params => {
  try {
    const page = await request.get('/cost/invalids/page', { params: { ...params, current: 1, size: 1000 } })
    const records = page?.records || []
    const totalAmount = records.reduce((s, r) => s + (Number(r.amount) || 0), 0)
    const confirmedAmount = records.filter(r => r.status === 'CONFIRMED' || r.status === 'APPROVING').reduce((s, r) => s + (Number(r.amount) || 0), 0)
    const byDept = {}
    for (const r of records) {
      if (!r.responsibleDept) continue
      byDept[r.responsibleDept] = (byDept[r.responsibleDept] || 0) + (Number(r.amount) || 0)
    }
    let topDept = null
    let topVal = -Infinity
    for (const [dept, val] of Object.entries(byDept)) {
      if (val > topVal) { topVal = val; topDept = dept }
    }
    return { totalAmount, confirmedAmount, count: records.length, topDept }
  } catch {
    return { totalAmount: 0, confirmedAmount: 0, count: 0, topDept: null }
  }
}
export const createInvalid = data => request.post('/cost/invalids', { invalid: data })
export const updateInvalid = data => request.put('/cost/invalids', { invalid: data })
export const deleteInvalid = id => request.delete(`/cost/invalids/${id}`)
export const submitInvalid = id => request.post(`/cost/invalids/${id}/submit`)

// ========== 成本指标库 ==========
export const fetchIndicators = params => request.get('/cost/indicators/page', { params })
export const fetchIndicatorSummary = async params => {
  try {
    const page = await request.get('/cost/indicators/page', { params: { ...params, current: 1, size: 1000 } })
    const records = page?.records || []
    return {
      count: records.length,
      costCount: records.filter(r => r.indicatorType === 'COST').length,
      contentCount: records.filter(r => r.indicatorType === 'CONTENT').length
    }
  } catch {
    return { count: 0, costCount: 0, contentCount: 0 }
  }
}
export const createIndicator = data => request.post('/cost/indicators', data)
export const updateIndicator = data => request.put('/cost/indicators', data)
export const deleteIndicator = id => request.delete(`/cost/indicators/${id}`)
export const suggestIndicators = params => request.get('/cost/indicators/suggest', { params })

// ========== 补充协议 ==========
export const fetchSupplements = params => request.get('/cost/supplements/page', { params })
export const fetchSupplementDetail = id => request.get(`/cost/supplements/${id}`)
export const createSupplement = data => request.post('/cost/supplements', data)
export const updateSupplement = data => request.put('/cost/supplements', data)
export const deleteSupplement = id => request.delete(`/cost/supplements/${id}`)
export const submitSupplement = id => request.post(`/cost/supplements/${id}/submit`)

// ========== 合同模板 ==========
export const fetchTemplates = params => request.get('/cost/templates/page', { params })
export const fetchTemplate = id => request.get(`/cost/templates/${id}`)
export const deleteTemplate = id => request.delete(`/cost/templates/${id}`)
// 上传 .docx 模板（multipart）
export const uploadTemplate = (file, meta = {}) => {
  const fd = new FormData()
  fd.append('file', file)
  if (meta.name) fd.append('name', meta.name)
  if (meta.contractType) fd.append('contractType', meta.contractType)
  if (meta.description) fd.append('description', meta.description)
  return request.post('/cost/templates', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
// 按模板 + 合同数据生成 .docx
export const generateContract = (templateId, contractId) =>
  request.post(`/cost/templates/${templateId}/generate`, null, { params: { contractId } })
// 文件 URL（预览/下载，走 /files 代理，不带 /api 前缀）
export const fileUrl = path => `/files/${path}`
