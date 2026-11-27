import request from './request'

// ================= 审批中心 =================

// 待办任务分页
export const todoPage = params => request.get('/workflow/todos/page', { params })

// 已办任务分页
export const donePage = params => request.get('/workflow/dones/page', { params })

// 我发起的流程实例分页
export const minePage = params => request.get('/workflow/mine/page', { params })

// 流程实例监控分页（status/bizType 过滤）
export const instancePage = params => request.get('/workflow/instances/page', { params })

// 实例统计（在途/超时/本月完成/平均审批时长）
export const instanceStats = () => request.get('/workflow/instances/stats')

// 实例详情 + 全部审批任务（流程追踪）
export const instanceDetail = id => request.get(`/workflow/instances/${id}`)

// 同意（comment 可空）
export const approveTask = (taskId, comment) =>
  request.post(`/workflow/tasks/${taskId}/approve`, { comment })

// 驳回（comment 必填）
export const rejectTask = (taskId, comment) =>
  request.post(`/workflow/tasks/${taskId}/reject`, { comment })

// 转交 { toUserId, toUserName, comment }
export const transferTask = (taskId, data) =>
  request.post(`/workflow/tasks/${taskId}/transfer`, data)

// 发起人撤回在途流程 { bizType, bizId }
export const withdrawInstance = data => request.post('/workflow/withdraw', data)

// 加签 { userIds, before } before=true 前加签(同节点会签) / false 后加签(插入新节点)
export const addSign = (taskId, userIds, before) =>
  request.post(`/workflow/tasks/${taskId}/add-sign`, { userIds, before })

// 抄送 { userIds, content }
export const ccTask = (taskId, userIds, content) =>
  request.post(`/workflow/tasks/${taskId}/cc`, { userIds, content })

// 我收到的抄送（分页）
export const myCcPage = params => request.get('/workflow/ccs/my', { params })

// 批量审批 { taskIds, comment } → 返回 [{taskId, success, msg}]
export const batchApprove = (taskIds, comment) =>
  request.post('/workflow/tasks/batch-approve', { taskIds, comment })

// 流程模板分页（kw 关键字）
export const templatePage = params => request.get('/workflow/templates/page', { params })

// 流程模板详情（含 definition JSON）
export const templateDetail = id => request.get(`/workflow/templates/${id}`)

// 新增/编辑流程模板（definition 为 JSON 字符串）
export const saveTemplate = data => request.post('/workflow/templates', data)

// 系统用户分页（转交选人）
export const userPage = params => request.get('/sys/users/page', { params })
