import request from './request'

// =====================================================
// 项目计划 API（严格对齐后端 /api/plan/** 真实端点）
// 后端：模板标准库 / WBS任务 / 进度填报 / 计划调整 / 里程碑 / 会议 / 形象进度 / 预警 / 报表
// 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data；
// code!==0 时 request.js 会 ElMessage.error(msg) 并 reject —— 前端只需不吞错即可展示后端提示
//   R1 里程碑锁定（不可直接改时间/调整须填延期原因归类）
//   R2 前置依赖（后置实际开始不得早于前置完成）
//   R3 红灯推送集团运营 + 滞后专项督办
//   R4 形象进度校验（进度款申请须关联已确认单且申请%不得高于已确认%）
// =====================================================

// ================= 公共选项（复用 MDM 已有接口） =================
/** 项目下拉：mdm 项目选项 */
export const fetchProjectOptions = () => request.get('/mdm/projects/options')

// ================= 状态与字典 =================
/** 模板状态 */
export const TEMPLATE_STATUS = { ACTIVE: ['生效', 'success'], ARCHIVED: ['已归档', 'info'] }
/** WBS 任务状态 */
export const TASK_STATUS = {
  PLANNED: ['已计划', 'info'], IN_PROGRESS: ['进行中', 'warning'],
  COMPLETED: ['已完成', 'success'], DELAYED: ['滞后', 'danger']
}
/** 预警色 GREEN/YELLOW/RED */
export const ALERT_STATUS = { GREEN: ['正常', 'success'], YELLOW: ['预警', 'warning'], RED: ['滞后', 'danger'] }
/** 计划调整状态 */
export const ADJUST_STATUS = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'],
  APPROVED: ['已通过', 'success'], REJECTED: ['已驳回', 'danger']
}
/** 进度填报状态 */
export const PROGRESS_STATUS = { SUBMITTED: ['已提交', 'warning'], CONFIRMED: ['已确认', 'success'] }
/** 会议类型 / 状态 */
export const MEETING_TYPE = { REGULAR: ['例会', 'info'], SPECIAL: ['专题', 'warning'] }
export const MEETING_STATUS = { DONE: ['已完成', 'success'], CANCELLED: ['已取消', 'info'] }
/** 会议决议任务状态 */
export const MEETING_TASK_STATUS = { PENDING: ['待办', 'warning'], DONE: ['已完成', 'success'], OVERDUE: ['超期', 'danger'] }
/** 形象进度状态 */
export const IMAGE_STATUS = { PENDING: ['待确认', 'warning'], CONFIRMED: ['已确认', 'success'] }

/** 专业/责任条线（设计/报建/工程/营销/交付） */
export const SPECIALTIES = ['设计', '报建', '工程', '营销', '交付']
export const specialtyLabel = v => v || '-'
/** WBS 层级（里程碑/一级/二级/三级） */
export const LEVELS = ['里程碑', '一级', '二级', '三级']
/** 延期原因归类 */
export const REASON_CATEGORIES = ['政策', '资金', '施工', '营销', '其他']
/** 形象进度部位 */
export const IMAGE_PARTS = ['基础', '主体', '装修', '机电']

/** 取 [中文名, tagType]，兜底 [原值, 'info'] */
export const statusMeta = (s, map) => {
  const m = (map && map[s]) || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}
/** 预警色 tag：GREEN success / YELLOW warning / RED danger */
export const alertMeta = s => ALERT_STATUS[s] || [s || '-', 'info']

// ================= 计划标准库（模板 + 节点库）/api/plan/templates =================
/** 模板分页 */
export const templatePage = params => request.get('/plan/templates/page', { params })
export const templateDetail = id => request.get(`/plan/templates/${id}`)
/** 新建模板：{ name, bizType, scale, tplVersion, status } */
export const templateCreate = data => request.post('/plan/templates', data)
export const templateUpdate = (id, data) => request.put(`/plan/templates/${id}`, data)
/** 模板节点库（List<PlanNodeLib>） */
export const templateNodes = id => request.get(`/plan/templates/${id}/nodes`)
/** 新增节点：POST /nodes?templateId=  body { nodeName, level, baseDuration, respLine, alertRule, sortNo } */
export const nodeCreate = (templateId, data) => request.post('/plan/templates/nodes', data, { params: { templateId } })
export const nodeUpdate = (nodeId, data) => request.put(`/plan/templates/nodes/${nodeId}`, data)
export const nodeDelete = nodeId => request.delete(`/plan/templates/nodes/${nodeId}`)

// ================= WBS 任务（主项+专项）/api/plan/tasks =================
/** 任务分页 */
export const taskPage = params => request.get('/plan/tasks/page', { params })
/** 专项计划：按专业过滤（复用 /page 逻辑） */
export const taskSpecialtyPage = params => request.get('/plan/tasks/specialty/page', { params })
/** 项目全部任务（List，不分页） */
export const taskList = projectId => request.get('/plan/tasks/list', { params: { projectId } })
export const taskDetail = id => request.get(`/plan/tasks/${id}`)
/** 从模板生成项目 WBS：POST /generate?templateId&projectId&startDate → 返回生成条数 */
export const taskGenerate = (templateId, projectId, startDate) =>
  request.post('/plan/tasks/generate', null, { params: { templateId, projectId, startDate: startDate || undefined } })
/** 进度填报：POST /{id}/report-progress { progressPct, actualStart, actualEnd }（R2 前置依赖）→ 返回更新后任务 */
export const taskReportProgress = (id, data) => request.post(`/plan/tasks/${id}/report-progress`, data)
/** 改计划时间：PUT /{id}/schedule { planStart, planEnd }（R1 里程碑阻断） */
export const taskUpdateSchedule = (id, data) => request.put(`/plan/tasks/${id}/schedule`, data)

// ================= 计划调整 /api/plan/adjusts =================
export const adjustPage = params => request.get('/plan/adjusts/page', { params })
export const adjustDetail = id => request.get(`/plan/adjusts/${id}`)
/** 登记调整单：{ taskId, reason, reasonCategory, newStart, newEnd, impactAnalysis }（R1 里程碑须填归类）→ 返回ID */
export const adjustCreate = data => request.post('/plan/adjusts', data)
/** 提交审批（PLAN_ADJUST：项目运营→集团运营） */
export const adjustSubmit = id => request.post(`/plan/adjusts/${id}/submit`)

// ================= 进度填报（周报）/api/plan/progress =================
export const progressPage = params => request.get('/plan/progress/page', { params })
/** 登记周报（回写任务进度/实际日期）：{ taskId, projectId, progressPct, description, actualStart, actualEnd } */
export const progressRecord = data => request.post('/plan/progress', data)
/** 周汇总：最近 N 周 { reportWeek, taskCount, avgProgress }[] */
export const progressWeekly = (projectId, weeks) => request.get('/plan/progress/weekly', { params: { projectId, weeks: weeks || 8 } })
/** 仅登记实际日期：{ taskId, actualStart, actualEnd } */
export const progressRegisterDates = data => request.post('/plan/progress/register-dates', data)

// ================= 预警 /api/plan/alerts =================
/** 重算项目全部任务预警色 → 返回变更条数 */
export const alertRecompute = projectId => request.post('/plan/alerts/recompute', null, { params: { projectId } })
/** 滞后任务清单（YELLOW/RED）→ List */
export const alertDelayed = projectId => request.get('/plan/alerts/delayed', { params: { projectId } })
/** 滞后连锁影响分析：{ taskId, taskName, overdueDays, downstreamCount, downstream:[] } */
export const alertImpact = taskId => request.get(`/plan/alerts/${taskId}/impact`)
/** R3 红灯推送 + 督办清单：{ redCount, yellowCount, supervisionList:[], watchList:[], pushRed } */
export const alertSupervision = projectId => request.get('/plan/alerts/supervision', { params: { projectId } })

// ================= 里程碑 /api/plan/milestones =================
/** 里程碑台账：[ { id, taskCode, taskName, planStart, planEnd, baselineEnd, actualStart, actualEnd, progressPct, status, alertStatus, overdueDays } ] */
export const milestoneLedger = projectId => request.get('/plan/milestones/ledger', { params: { projectId } })
/** 达成率 + 延期原因归类：{ projectId, total, completed, inProgress, delayed, achievementRate, delayedByCategory:{} } */
export const milestoneAchievement = projectId => request.get('/plan/milestones/achievement', { params: { projectId } })

// ================= 会议 + 决议任务 /api/plan/meetings =================
export const meetingPage = params => request.get('/plan/meetings/page', { params })
export const meetingDetail = id => request.get(`/plan/meetings/${id}`)
/** 新建会议：{ meetingNo, projectId, meetingType, meetingDate, agenda, minutes, status } */
export const meetingCreate = data => request.post('/plan/meetings', data)
/** 添加决议任务：POST /{id}/tasks { taskDesc, respUserId, respUserName, dueDate, status } */
export const meetingAddTask = (id, data) => request.post(`/plan/meetings/${id}/tasks`, data)
/** 决议任务闭环：标记完成 */
export const meetingTaskDone = taskId => request.post(`/plan/meetings/tasks/${taskId}/done`)
/** 超期提醒：标记并返回超期决议任务清单 */
export const meetingOverdue = () => request.get('/plan/meetings/overdue')

// ================= 形象进度（供 cost R4 校验）/api/plan/image-progress =================
export const imagePage = params => request.get('/plan/image-progress/page', { params })
export const imageDetail = id => request.get(`/plan/image-progress/${id}`)
/** 新增确认单：{ projectId, building, partName, confirmedPct, confirmDate, imageIds, status } */
export const imageCreate = data => request.post('/plan/image-progress', data)
/** 确认形象进度（CONFIRMED + 确认日期） */
export const imageConfirm = id => request.post(`/plan/image-progress/${id}/confirm`)
/** 已确认形象进度清单（供 cost 侧关联选择） */
export const imageConfirmed = (projectId, building) => request.get('/plan/image-progress/confirmed', { params: { projectId, building: building || undefined } })
/** 查询已确认%（供 cost 侧带出） */
export const imagePct = (projectId, building, partName) => request.get('/plan/image-progress/pct', { params: { projectId, building, partName } })
/** R4 校验（供 cost 进度款申请调用）：{ projectId, building, partName, applyPct } */
export const imageValidate = data => request.post('/plan/image-progress/validate', data)

// ================= 计划总览 /api/plan/overview =================
/**
 * 计划总览聚合（一次请求）：
 * { kpi, subway:[里程碑台账], specialty:[专项进度], construction:[已确认形象进度], alert:{ distribution, red, yellow, pushRed, delayedByCategory, ranking } }
 */
export const planOverview = projectId => request.get('/plan/overview', { params: { projectId } })

// ================= 计划报表 /api/plan/reports =================
/** 甘特图数据（节点/计划区间/基线/实际/进度/预警色）→ List<Map> */
export const reportGantt = projectId => request.get('/plan/reports/gantt', { params: { projectId } })
/** 里程碑达成月报：{ projectId, month, ledger:[], achievement:{} } */
export const reportMilestone = projectId => request.get('/plan/reports/milestone', { params: { projectId } })
/** 各单位执行排名（红黄绿统计 + 完成率）→ List<Map> */
export const reportRanking = projectId => request.get('/plan/reports/ranking', { params: { projectId: projectId || undefined } })
