import request from './request'

// =====================================================
// AI 数字员工 API（严格对齐后端 /api/ai/** 真实端点）
// 后端 com.reip.ai.controller.*：approval / query / report / chat / kb / model-config / audit
// 核心原则：未配大模型 → 规则引擎兜底(aiMode=RULE)；配好模型 → 真实 LLM 增强(aiMode=LLM)
// 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data；code!==0 时
//   request.js 会 ElMessage.error(msg) 并 reject —— 前端不吞错即可展示后端提示
// 注意：request.js baseURL='/api'，此处路径均不带 /api 前缀
// =====================================================

// ================= 公共字典 / 标签 =================
/** 审批预审业务类型（对齐 AiApprovalService 的 switch 分支） */
export const BIZ_TYPES = [
  { value: 'SALE_CONTRACT', label: '销售合同' },
  { value: 'PROC_AWARD', label: '定标审批' },
  { value: 'EXP_REIMBURSE', label: '费用报销' },
  { value: 'COST_CHANGE', label: '成本变更' }
]
export const bizTypeLabel = v => BIZ_TYPES.find(t => t.value === v)?.label || v || '-'

/** 报告类型（对齐 AiReportService.normalize 识别规则：销售/成本/采购） */
export const REPORT_TYPES = [
  { value: 'SALES', label: '销售日报' },
  { value: 'COST', label: '成本月报' },
  { value: 'PROC', label: '采购分析' },
  { value: 'EMPLOYEE', label: '数字员工' }
]
export const reportTypeLabel = v => REPORT_TYPES.find(t => t.value === v)?.label || v || '-'

/** 知识库分类（种子数据：制度/指标/案例） */
export const KB_CATEGORIES = [
  { value: '制度', label: '制度' },
  { value: '指标', label: '指标' },
  { value: '案例', label: '案例' }
]

/** 模型 provider（对齐 ai_model_config.provider） */
export const MODEL_PROVIDERS = [
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容' },
  { value: 'QWEN', label: '通义千问(Qwen)' },
  { value: 'DEEPSEEK', label: 'DeepSeek' }
]
export const providerLabel = v => MODEL_PROVIDERS.find(t => t.value === v)?.label || v || '-'

/** 模型配置状态 */
export const MODEL_STATUS = { ACTIVE: ['启用', 'success'], INACTIVE: ['停用', 'info'] }

/** 知识库状态 */
export const KB_STATUS = { ACTIVE: ['启用', 'success'], INACTIVE: ['停用', 'info'], ARCHIVED: ['已归档', 'info'] }

/** 审计场景（对齐 ai_audit_log.scene） */
export const AUDIT_SCENES = {
  APPROVAL_PREVIEW: '审批预审', QUERY: '指标问答', REPORT: '报告生成',
  QA: 'AI问答', MODEL_TEST: '模型测试'
}
export const sceneLabel = v => AUDIT_SCENES[v] || v || '-'

/** 审计状态 */
export const AUDIT_STATUS = { SUCCESS: ['成功', 'success'], FAIL: ['失败', 'danger'], DEGRADED: ['降级', 'warning'] }

/** 审批风险等级（对齐 AiApprovalService.risk：HIGH / MEDIUM） */
export const RISK_LEVEL = { HIGH: ['高危', 'danger'], MEDIUM: ['中危', 'warning'], LOW: ['低危', 'info'] }

/** AI 模式徽标：RULE 灰 / LLM 蓝 */
export const aiModeMeta = m => ({
  LLM: ['LLM 大模型', 'primary'],
  'KB-LLM': ['知识库·LLM', 'success'],
  KB: ['知识库', 'success'],
  EMPLOYEE: ['数字员工', 'warning'],
  RULE: ['规则引擎', 'info'],
}[m] || ['规则引擎', 'info'])

/** 取 [中文名, tagType]，兜底 [原值, 'info'] */
export const statusMeta = (s, map) => {
  const m = (map && map[s]) || [s || '-', 'info']
  return [m[0], m[1] || 'info']
}

/** 项目下拉（报告生成/指标问答可选范围）：mdm 项目选项 → List<{ id, name }> */
export const fetchProjectOptions = () => request.get('/mdm/projects/options')

// ================= 审批预审 /api/ai/approval =================
/** AI 预审：GET /api/ai/approval/preview?bizType=&bizId=
 *  返回 { bizType, bizId, summary:[], risks:[{level,desc,evidence}], similar:[...], aiMode, llmNote, advisory } */
export const approvalPreview = (bizType, bizId) =>
  request.get('/ai/approval/preview', { params: { bizType, bizId } })

// ================= 指标问答 /api/ai/query =================
/** 自然语言→指标：GET /api/ai/query?q=&projectId=
 *  命中 { question,matched:true,metricCode,metricName,value,unit,detail,caliber,source,answer,aiMode,llmNote }
 *  未命中 { question,matched:false,answer,aiMode } */
export const aiQuery = (q, projectId) =>
  request.get('/ai/query', { params: { q, projectId } })

// ================= 报告生成 /api/ai/report =================
/** 生成报告：GET /api/ai/report/generate?type=&projectId=&month=
 *  返回 { title,type,projectId,month,sections:[{name,data}],conclusions:[],advisory,aiMode,taskId,taskCode } */
export const reportGenerate = (type, projectId, month) =>
  request.get('/ai/report/generate', { params: { type, projectId, month } })
/** 报告列表（任务中心）：GET /api/ai/report/list?current=&size= → PageResult<AiTask> */
export const reportList = params => request.get('/ai/report/list', { params })
/** 报告详情：GET /api/ai/report/{id} → AiTask { taskCode,type,status,title,input,output } */
export const reportDetail = id => request.get(`/ai/report/${id}`)
/** 数字员工模式生成报告：POST /api/ai/report/generate-by-employee/{taskId} */
export const reportByEmployee = taskId => request.post(`/ai/report/generate-by-employee/${taskId}`)

// ================= AI 会话 /api/ai/chat =================
/** 新建会话：POST /api/ai/chat/sessions  body { title? } → AiChatSession { id,sessionId,userId,title } */
export const chatCreateSession = (title) => request.post('/ai/chat/sessions', { title })
/** 我的会话列表：GET /api/ai/chat/sessions → List<AiChatSession> */
export const chatSessions = () => request.get('/ai/chat/sessions')
/** 会话消息：GET /api/ai/chat/sessions/{sessionId}/messages → List<AiChatMessage { id,sessionId,role,content,aiMode,refs } > */
export const chatMessages = sessionId => request.get(`/ai/chat/sessions/${sessionId}/messages`)
/** 发送消息：POST /api/ai/chat/sessions/{sessionId}/send  body { question, mode, taskId }
 *  mode: METRIC（指标问答）/ KB（知识库）/ EMPLOYEE（数字员工，需 taskId）
 *  返回 { sessionId, userMessageId, assistantMessageId, answer }（answer 随 mode 而不同） */
export const chatSend = (sessionId, question, mode = 'METRIC', taskId = null) =>
  request.post(`/ai/chat/sessions/${sessionId}/send`, { question, mode, taskId })
/** 删除会话：DELETE /api/ai/chat/sessions/{sessionId} */
export const chatDeleteSession = sessionId => request.delete(`/ai/chat/sessions/${sessionId}`)

// ================= 知识库 /api/ai/kb =================
/** 分页：GET /api/ai/kb/page?current=&size=&category=&kw= → PageResult<AiKbDocument> */
export const kbPage = params => request.get('/ai/kb/page', { params })
/** 检索：GET /api/ai/kb/search?kw=&limit= → List<AiKbDocument> */
export const kbSearch = (kw, limit = 10) => request.get('/ai/kb/search', { params: { kw, limit } })
/** 详情：GET /api/ai/kb/{id} */
export const kbDetail = id => request.get(`/ai/kb/${id}`)
/** 新增：POST /api/ai/kb  body { docCode,title,category,content,status } → Long */
export const kbCreate = data => request.post('/ai/kb', data)
/** 更新：PUT /api/ai/kb/{id}  body AiKbDocument */
export const kbUpdate = (id, data) => request.put(`/ai/kb/${id}`, data)
/** 删除：DELETE /api/ai/kb/{id} */
export const kbDelete = id => request.delete(`/ai/kb/${id}`)
/** 知识图谱：GET /api/ai/kb/graph → {nodes, edges} */
export const kbGraph = () => request.get('/ai/kb/graph')
/** 关联文档：GET /api/ai/kb/related/{id} */
export const kbRelated = id => request.get(`/ai/kb/related/${id}`)
/** 添加关联：POST /api/ai/kb/link */
export const kbAddLink = data => request.post('/ai/kb/link', data)
/** 删除关联：DELETE /api/ai/kb/link/{sourceId}/{targetId} */
export const kbRemoveLink = (src, tgt) => request.delete(`/ai/kb/link/${src}/${tgt}`)
/** Wiki 列表：GET /api/ai/kb/wiki */
export const kbWiki = () => request.get('/ai/kb/wiki')

// ================= 模型配置 /api/ai/model-config =================
/** 分页：GET /api/ai/model-config/page?current=&size=&provider=&status= → PageResult<AiModelConfig>（apiKey 置空，返回 apiKeyMasked） */
export const modelPage = params => request.get('/ai/model-config/page', { params })
/** 全量列表：GET /api/ai/model-config/list */
export const modelList = () => request.get('/ai/model-config/list')
/** 详情：GET /api/ai/model-config/{id} */
export const modelDetail = id => request.get(`/ai/model-config/${id}`)
/** 新增：POST /api/ai/model-config  body { provider,modelName,baseUrl,apiKey,timeoutMs,temperature,status,remark } → Long */
export const modelCreate = data => request.post('/ai/model-config', data)
/** 更新：PUT /api/ai/model-config/{id}（apiKey 为空表示不清空密钥） */
export const modelUpdate = (id, data) => request.put(`/ai/model-config/${id}`, data)
/** 删除：DELETE /api/ai/model-config/{id} */
export const modelDelete = id => request.delete(`/ai/model-config/${id}`)
/** 测试连接：POST /api/ai/model-config/{id}/test → { success,message,durationMs,degraded,model } */
export const modelTest = id => request.post(`/ai/model-config/${id}/test`)

// ================= 审计日志 /api/ai/audit =================
/** 分页列表：GET /api/ai/audit?current=&size=&scene=&aiMode=&status= → PageResult<AiAuditLog> */
export const auditPage = params => request.get('/ai/audit', { params })
/** 详情：GET /api/ai/audit/{id} → AiAuditLog { scene,userId,modelConfigId,inputDigest,outputDigest,tokenIn,tokenOut,cost,durationMs,aiMode,status,createdAt } */
export const auditDetail = id => request.get(`/ai/audit/${id}`)
