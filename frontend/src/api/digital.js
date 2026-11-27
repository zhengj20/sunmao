import request from './request'

// =====================================================
// 数字员工团队 API（严格对齐后端 /api/digital/** 真实端点）
// 后端 com.reip.digital.controller.*：employee / skill / task
// 核心原则：配置大模型 → LLM 智能分析；未配置/失败 → 规则引擎兜底(ai_mode=RULE)
// =====================================================

/** 角色（数字员工） */
export const ROLE_CODES = [
  { value: 'PROC', label: '招采', avatar: 'proc' },
  { value: 'COST', label: '成本', avatar: 'cost' },
  { value: 'FIN', label: '财务', avatar: 'fin' },
  { value: 'SAL', label: '营销', avatar: 'sal' },
  { value: 'LEGAL', label: '法务', avatar: 'legal' },
  { value: 'PROJ', label: '项目工程', avatar: 'proj' },
  { value: 'CUSTOM', label: '自定义', avatar: 'custom' }
]
export const roleLabel = v => ROLE_CODES.find(t => t.value === v)?.label || v || '自定义'

/** 形象主题 */
export const AVATARS = [
  { value: 'proc', label: '深蓝·招采' },
  { value: 'cost', label: '墨绿·成本' },
  { value: 'fin', label: '紫晶·财务' },
  { value: 'sal', label: '暖橙·营销' },
  { value: 'legal', label: '琥珀·法务' },
  { value: 'proj', label: '钢蓝·工程' },
  { value: 'custom', label: '青银·通用' }
]

/** 技能分类 */
export const SKILL_CATEGORIES = {
  BUILTIN: ['内置', 'primary'],
  MARKET: ['市场', 'success'],
  CUSTOM: ['自定义', 'warning']
}
export const categoryMeta = v => (SKILL_CATEGORIES[v] || [v || '-', 'info'])

/** 取数域 */
export const DATA_SOURCES = [
  { value: 'PROC', label: '招采数据' },
  { value: 'COST', label: '成本数据' },
  { value: 'FIN', label: '财务数据' },
  { value: 'SAL', label: '营销数据' },
  { value: 'NONE', label: '通用（不取数）' }
]
export const dataSourceLabel = v => DATA_SOURCES.find(t => t.value === v)?.label || v || '-'

/** 触发方式 */
export const TRIGGER_TYPES = {
  SCHEDULED: ['定时', 'primary'],
  CONDITIONAL: ['条件', 'warning'],
  MANUAL: ['手动', 'info']
}
export const triggerMeta = v => (TRIGGER_TYPES[v] || [v || '-', 'info'])

/** 执行状态 */
export const RUN_STATUS = {
  RUNNING: ['执行中', 'primary'],
  DONE: ['已完成', 'success'],
  FAILED: ['失败', 'danger']
}
export const runStatusMeta = v => (RUN_STATUS[v] || [v || '-', 'info'])

/** 员工状态 */
export const EMP_STATUS = { ACTIVE: ['在岗', 'success'], INACTIVE: ['休假', 'info'] }
export const empStatusMeta = v => (EMP_STATUS[v] || [v || '-', 'info'])

// ================= 员工团队 /api/digital/employee =================
/** 团队看板：员工+技能+任务+模型+最近运行+是否在运行 */
export const empBoard = () => request.get('/digital/employee/board')
/** 员工列表（下拉用） */
export const empList = () => request.get('/digital/employee/list')
/** 员工详情（含技能） */
export const empDetail = id => request.get(`/digital/employee/${id}`)
/** 新增：{ employee:{...}, skillIds:[...] } → Long */
export const empCreate = data => request.post('/digital/employee', data)
/** 更新：{ employee:{...}, skillIds:[...]或null } */
export const empUpdate = (id, data) => request.put(`/digital/employee/${id}`, data)
/** 删除 */
export const empDelete = id => request.delete(`/digital/employee/${id}`)
/** 全量重装配技能 */
export const empSetSkills = (id, skillIds) => request.post(`/digital/employee/${id}/skills`, { skillIds })
/** 快捷切换在岗/休假：ACTIVE / INACTIVE */
export const empSetStatus = (id, status) => request.post(`/digital/employee/${id}/status`, { status })
/** 员工已装配技能 */
export const empSkills = id => request.get(`/digital/employee/${id}/skills`)
/** 手动执行（异步）：{ taskId } → { runId } */
export const empRun = (id, taskId) => request.post(`/digital/employee/${id}/run`, { taskId })

// ================= 技能市场 /api/digital/skill =================
/** 列表：GET /digital/skill/list?kw=&category= */
export const skillList = params => request.get('/digital/skill/list', { params })
/** 详情 */
export const skillDetail = id => request.get(`/digital/skill/${id}`)
/** 新增：{ skillCode,name,category,dataSource,icon,description,promptTemplate,source } → Long */
export const skillCreate = data => request.post('/digital/skill', data)
/** 更新（提示词模板等全部可编辑） */
export const skillUpdate = (id, data) => request.put(`/digital/skill/${id}`, data)
/** 删除（内置不可删） */
export const skillDelete = id => request.delete(`/digital/skill/${id}`)
/** 安装到员工：{ employeeIds } */
export const skillInstall = (id, employeeIds) => request.post(`/digital/skill/${id}/install`, { employeeIds })

// ================= 任务 /api/digital/task =================
/** 任务列表：GET /digital/task/list?employeeId= */
export const taskList = params => request.get('/digital/task/list', { params })
/** 任务详情 */
export const taskDetail = id => request.get(`/digital/task/${id}`)
/** 新增任务 → Long */
export const taskCreate = data => request.post('/digital/task', data)
/** 更新任务 */
export const taskUpdate = (id, data) => request.put(`/digital/task/${id}`, data)
/** 删除任务 */
export const taskDelete = id => request.delete(`/digital/task/${id}`)
/** 手动执行（异步）→ runId */
export const taskRun = id => request.post(`/digital/task/${id}/run`)

// ================= 执行记录 /api/digital/task/runs =================
/** 分页：GET /digital/task/runs?current=&size=&employeeId=&status= */
export const runPage = params => request.get('/digital/task/runs', { params })
/** 详情（steps 为 JSON 字符串，前端 parse） */
export const runDetail = id => request.get(`/digital/task/runs/${id}`)
/** 执行统计：总量/成功率/平均耗时/LLM占比 + 近7天趋势 */
export const runStats = () => request.get('/digital/task/runs/stats')
/** 条件触发可用指标：{ PROC:[{key,label}], COST:[...], FIN:[...], SAL:[...] } */
export const condMetrics = () => request.get('/digital/task/cond-metrics')

// ================= 工作流 /api/digital/workflow =================
export const wfBoard = () => request.get('/digital/workflow/board')
export const wfCreate = data => request.post('/digital/workflow', data)
export const wfUpdate = (id, data) => request.put(`/digital/workflow/${id}`, data)
export const wfDelete = id => request.delete(`/digital/workflow/${id}`)
export const wfRun = id => request.post(`/digital/workflow/${id}/run`)
export const wfRuns = params => request.get('/digital/workflow/runs', { params })
export const wfRunDetail = id => request.get(`/digital/workflow/runs/${id}`)
/** 待人工审批列表 */
export const wfApprovals = () => request.get('/digital/workflow/approvals')
/** 审批决策：decision = approve 继续 / reject 终止 */
export const wfDecide = (id, decision, comment) => request.post(`/digital/workflow/approvals/${id}/decide`, { decision, comment })
/** 审批历史（已决策） */
export const wfApprovalHistory = (limit = 20) => request.get('/digital/workflow/approvals/history', { params: { limit } })

// ================= 工具 /api/digital/tools =================
export const toolList = () => request.get('/digital/tools')
export const toolSave = data => request.post('/digital/tools', data)
export const toolDelete = id => request.delete(`/digital/tools/${id}`)
export const toolTest = (toolId, args) => request.post('/digital/tools/test', { toolId, args })
