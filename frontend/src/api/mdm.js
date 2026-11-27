import request from './request'

// ================= 组织机构 =================
export const orgTree = () => request.get('/mdm/orgs/tree')
export const orgCreate = data => request.post('/mdm/orgs', data)
export const orgUpdate = data => request.put('/mdm/orgs', data)
export const orgDelete = id => request.delete(`/mdm/orgs/${id}`)

// ================= 员工 =================
export const employeePage = params => request.get('/mdm/employees/page', { params })
export const employeeDetail = id => request.get(`/mdm/employees/${id}`)
export const employeeCreate = data => request.post('/mdm/employees', data)
export const employeeUpdate = data => request.put('/mdm/employees', data)
export const employeeDelete = id => request.delete(`/mdm/employees/${id}`)

// ================= 项目 =================
export const projectPage = params => request.get('/mdm/projects/page', { params })
export const projectOptions = () => request.get('/mdm/projects/options')
export const projectDetail = id => request.get(`/mdm/projects/${id}`)
export const projectCreate = data => request.post('/mdm/projects', data)
export const projectUpdate = data => request.put('/mdm/projects', data)
export const projectChangeStatus = (id, status) =>
  request.put(`/mdm/projects/${id}/status`, null, { params: { status } })
export const projectDelete = id => request.delete(`/mdm/projects/${id}`)

// 项目分期
export const phaseList = projectId => request.get(`/mdm/projects/${projectId}/phases`)
export const phaseCreate = (projectId, data) => request.post(`/mdm/projects/${projectId}/phases`, data)
export const phaseUpdate = (projectId, phaseId, data) =>
  request.put(`/mdm/projects/${projectId}/phases/${phaseId}`, data)
export const phaseDelete = (projectId, phaseId) =>
  request.delete(`/mdm/projects/${projectId}/phases/${phaseId}`)

// 业态
export const productTypeList = projectId => request.get(`/mdm/projects/${projectId}/product-types`)
export const productTypeCreate = (projectId, data) =>
  request.post(`/mdm/projects/${projectId}/product-types`, data)
export const productTypeUpdate = (projectId, productTypeId, data) =>
  request.put(`/mdm/projects/${projectId}/product-types/${productTypeId}`, data)
export const productTypeDelete = (projectId, productTypeId) =>
  request.delete(`/mdm/projects/${projectId}/product-types/${productTypeId}`)

// 楼栋
export const buildingList = projectId => request.get(`/mdm/projects/${projectId}/buildings`)
export const buildingCreate = (projectId, data) => request.post(`/mdm/projects/${projectId}/buildings`, data)
export const buildingUpdate = (projectId, buildingId, data) =>
  request.put(`/mdm/projects/${projectId}/buildings/${buildingId}`, data)
export const buildingDelete = (projectId, buildingId) =>
  request.delete(`/mdm/projects/${projectId}/buildings/${buildingId}`)

// ================= 房间 =================
export const roomPage = params => request.get('/mdm/rooms/page', { params })
export const roomGenerate = data => request.post('/mdm/rooms/generate', data)
export const roomUpdate = data => request.put('/mdm/rooms', data)
export const roomDelete = id => request.delete(`/mdm/rooms/${id}`)
// 楼栋房源看板
/** 楼栋汇总（每栋房间数/面积/可售/已售/货值） */
export const buildingSummary = projectId => request.get('/mdm/rooms/summary', { params: { projectId } })
/** 楼栋房间明细（可视化看板，含表价/底价/销控状态） */
export const buildingRooms = buildingId => request.get(`/mdm/rooms/building/${buildingId}`)
/** 合并房间（同楼栋/楼层/单元且均可售） */
export const roomMerge = roomIds => request.post('/mdm/rooms/merge', { roomIds })

// ================= 成本科目 =================
export const accountTree = params => request.get('/mdm/accounts/tree', { params })
export const accountClone = projectId => request.post('/mdm/accounts/clone', { projectId })
export const accountCreate = data => request.post('/mdm/accounts', data)
export const accountUpdate = data => request.put('/mdm/accounts', data)
export const accountDelete = id => request.delete(`/mdm/accounts/${id}`)

// ================= 费用科目（三费：管理/销售/财务，与成本科目分离） =================
export const expenseAccountTree = params => request.get('/mdm/expense-accounts/tree', { params })
export const expenseAccountCreate = data => request.post('/mdm/expense-accounts', data)
export const expenseAccountUpdate = data => request.put('/mdm/expense-accounts', data)
export const expenseAccountDelete = id => request.delete(`/mdm/expense-accounts/${id}`)

// ================= 分类字典 =================
export const categoryTree = params => request.get('/mdm/categories/tree', { params })
export const categoryCreate = data => request.post('/mdm/categories', data)
export const categoryUpdate = data => request.put('/mdm/categories', data)
export const categoryDelete = id => request.delete(`/mdm/categories/${id}`)

// ================= 供应商 =================
export const supplierPage = params => request.get('/mdm/suppliers/page', { params })
export const supplierDetail = id => request.get(`/mdm/suppliers/${id}`)
export const supplierCreate = data => request.post('/mdm/suppliers', data)
export const supplierUpdate = data => request.put('/mdm/suppliers', data)
export const supplierDelete = id => request.delete(`/mdm/suppliers/${id}`)
export const supplierBlacklist = (id, reason) => request.post(`/mdm/suppliers/${id}/blacklist`, { reason })
export const supplierRestore = id => request.post(`/mdm/suppliers/${id}/restore`)
export const qualificationList = supplierId => request.get(`/mdm/suppliers/${supplierId}/qualifications`)
export const qualificationCreate = (supplierId, data) =>
  request.post(`/mdm/suppliers/${supplierId}/qualifications`, data)
export const qualificationUpdate = (supplierId, qualId, data) =>
  request.put(`/mdm/suppliers/${supplierId}/qualifications/${qualId}`, data)
export const qualificationDelete = (supplierId, qualId) =>
  request.delete(`/mdm/suppliers/${supplierId}/qualifications/${qualId}`)
export const qualificationExpiring = (days = 90) =>
  request.get('/mdm/suppliers/qualifications/expiring', { params: { days } })

// ================= 客户 =================
export const customerPage = params => request.get('/mdm/customers/page', { params })
export const customerDetail = id => request.get(`/mdm/customers/${id}`)
export const customerCreate = data => request.post('/mdm/customers', data)
export const customerUpdate = data => request.put('/mdm/customers', data)
export const customerDelete = id => request.delete(`/mdm/customers/${id}`)
export const relativeList = customerId => request.get(`/mdm/customers/${customerId}/relatives`)
export const relativeCreate = (customerId, data) => request.post(`/mdm/customers/${customerId}/relatives`, data)
export const relativeUpdate = (customerId, relativeId, data) =>
  request.put(`/mdm/customers/${customerId}/relatives/${relativeId}`, data)
export const relativeDelete = (customerId, relativeId) =>
  request.delete(`/mdm/customers/${customerId}/relatives/${relativeId}`)

// ================= 编码规则 =================
export const codeRulePage = params => request.get('/mdm/coderules/page', { params })
export const codeRulePreview = ruleCode => request.get(`/mdm/coderules/preview/${ruleCode}`)
export const codeRuleCreate = data => request.post('/mdm/coderules', data)
export const codeRuleUpdate = data => request.put('/mdm/coderules', data)
export const codeRuleDelete = id => request.delete(`/mdm/coderules/${id}`)

// ================= 统一码值字典 =================
export const dictList = key => request.get('/mdm/dicts', { params: { key } })
