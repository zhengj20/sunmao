import request from './request'

// =====================================================
// 设计审图（读图/审图）API（严格对齐后端 /api/dwg/** 真实端点）
// 后端 com.reip.dwg.controller.DwgController
// 核心原则：未配大模型 → 规则引擎产出审查要点(aiMode=RULE)；配好模型 → LLM 增强(aiMode=LLM)
// 统一响应 { code:0 成功, msg, data }，request.js 已自动解包 data
// =====================================================

// ---------- 字典（对齐后端 DwgDrawingService 常量） ----------
/** 专业 */
export const DISCIPLINES = [
  { value: 'ARCH', label: '建筑' },
  { value: 'STRUCT', label: '结构' },
  { value: 'ELEC', label: '电气' },
  { value: 'PLUMB', label: '给排水' },
  { value: 'HVAC', label: '暖通' },
  { value: 'INTERIOR', label: '精装修' },
  { value: 'LANDSCAPE', label: '景观' }
]
export const disciplineLabel = v => DISCIPLINES.find(d => d.value === v)?.label || v || '-'

/** 阶段 */
export const STAGES = [
  { value: 'SCHEME', label: '方案' },
  { value: 'CONSTRUCTION', label: '施工图' },
  { value: 'AS_BUILT', label: '竣工图' }
]
export const stageLabel = v => STAGES.find(s => s.value === v)?.label || v || '-'

/** 问题严重度 */
export const LEVELS = [
  { value: 'HIGH', label: '严重', type: 'danger' },
  { value: 'MEDIUM', label: '一般', type: 'warning' },
  { value: 'LOW', label: '建议', type: 'info' }
]
export const levelMeta = v => LEVELS.find(l => l.value === v) || { label: v || '-', type: 'info' }

/** 问题分类（建议值，可自由输入） */
export const CATEGORIES = ['图面表达', '规范符合性', '尺寸标注', '专业冲突', '材料设备', '安全消防', '节能环保', '其他']

/** 审查单状态 */
export const DRAWING_STATUS = {
  PENDING: ['待审', 'info'],
  REVIEWING: ['审查中', 'warning'],
  PASSED: ['通过', 'success'],
  REJECTED: ['驳回', 'danger']
}
export const drawingStatusMeta = s => DRAWING_STATUS[s] || [s || '-', 'info']

/** 问题状态 */
export const ISSUE_STATUS = {
  OPEN: ['待解决', 'warning'],
  RESOLVED: ['已解决', 'success'],
  REJECTED: ['无需处理', 'info']
}
export const issueStatusMeta = s => ISSUE_STATUS[s] || [s || '-', 'info']

/** 项目下拉（复用 mdm 接口） */
export const fetchProjectOptions = () => request.get('/mdm/projects/options')

// ---------- 图纸审查单 ----------
/** 上传图纸并创建审查单（multipart：file + 元信息） */
export const uploadDrawing = (file, meta = {}) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('projectId', meta.projectId)
  fd.append('discipline', meta.discipline)
  fd.append('title', meta.title)
  if (meta.version) fd.append('version', meta.version)
  if (meta.stage) fd.append('stage', meta.stage)
  if (meta.designUnit) fd.append('designUnit', meta.designUnit)
  if (meta.remark) fd.append('remark', meta.remark)
  return request.post('/dwg/drawings', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
/** 分页：GET /dwg/drawings/page */
export const fetchDrawings = params => request.get('/dwg/drawings/page', { params })
/** 详情：审查单 + 问题清单 + 统计 */
export const fetchDrawing = id => request.get(`/dwg/drawings/${id}`)
/** 删除审查单（仅待审/无未解决问题时） */
export const deleteDrawing = id => request.delete(`/dwg/drawings/${id}`)
/** 文件 URL（预览/下载，走 /files 代理） */
export const fileUrl = path => `/files/${path}`

// ---------- AI / 系统预审 ----------
/** 运行 AI/系统预审：生成审查要点(AI 问题) + 预审意见
 *  返回 { drawingId, aiMode, aiNote, pointCount, advisory } */
export const aiReview = id => request.post(`/dwg/drawings/${id}/ai-review`)

// ---------- 问题管理 ----------
/** 人工提出问题 */
export const addIssue = (drawingId, data) => request.post(`/dwg/drawings/${drawingId}/issues`, data)
/** 编辑问题 */
export const updateIssue = (id, data) => request.put(`/dwg/issues/${id}`, data)
/** 标记已解决（附解决说明） */
export const resolveIssue = (id, note) => request.post(`/dwg/issues/${id}/resolve`, { note })
/** 标记无需处理 */
export const dismissIssue = id => request.post(`/dwg/issues/${id}/dismiss`)
/** 删除未解决问题 */
export const deleteIssue = id => request.delete(`/dwg/issues/${id}`)

// ---------- 审查结论 ----------
/** 审查通过 */
export const passDrawing = id => request.post(`/dwg/drawings/${id}/pass`)
/** 审查驳回（附原因） */
export const rejectDrawing = (id, reason) => request.post(`/dwg/drawings/${id}/reject`, { reason })

// ---------- 汇总 ----------
/** 汇总：GET /dwg/summary?projectId=
 *  返回 { totalDrawings, passed, rejected, inReview, totalIssues, openIssues, resolvedIssues, rows } */
export const fetchSummary = params => request.get('/dwg/summary', { params })

// ---------- 审查要点库（AI 预审规则源，可配置） ----------
/** 分页：GET /dwg/checklist/page?discipline=&level=&kw= */
export const fetchChecklistPage = params => request.get('/dwg/checklist/page', { params })
/** 全部：GET /dwg/checklist/list */
export const fetchChecklistList = () => request.get('/dwg/checklist/list')
/** 新增要点 */
export const addChecklist = data => request.post('/dwg/checklist', data)
/** 编辑要点 */
export const updateChecklist = (id, data) => request.put(`/dwg/checklist/${id}`, data)
/** 删除要点 */
export const deleteChecklist = id => request.delete(`/dwg/checklist/${id}`)
/** 启用/停用切换 */
export const toggleChecklist = id => request.post(`/dwg/checklist/${id}/toggle`)

// =====================================================
// 审查报告（前端生成可打印/下载 HTML）
// 入参 detail：审查单对象（含 projectName/discipline/stage/drawingVersion/...）
//       issues：问题数组
// =====================================================
const esc = s => (s == null ? '' : String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'))
const levelTag = v => ({ HIGH: '严重', MEDIUM: '一般', LOW: '建议' })[v] || v || '-'
const issueTag = s => ({ OPEN: '待解决', RESOLVED: '已解决', REJECTED: '无需处理' })[s] || s || '-'
const discTag = v => DISCIPLINES.find(d => d.value === v)?.label || v || '-'
const stageTag = v => STAGES.find(s => s.value === v)?.label || v || '-'
const drawingTag = s => DRAWING_STATUS[s]?.[0] || s || '-'

/** 生成自包含的审查报告 HTML（内联样式，可打印/下载） */
export function buildReportHtml(detail, issues = [], advisory = '') {
  const list = [...issues].sort((a, b) => (a.seq || 0) - (b.seq || 0))
  const open = list.filter(i => i.status === 'OPEN').length
  const resolved = list.filter(i => i.status === 'RESOLVED').length
  const dismissed = list.filter(i => i.status === 'REJECTED').length
  const high = list.filter(i => i.level === 'HIGH').length
  const rows = list.map((i, n) => `
    <tr>
      <td>${n + 1}</td>
      <td>${i.source === 'AI' ? 'AI' : '人工'}</td>
      <td class="lv-${(i.level || '').toLowerCase()}">${levelTag(i.level)}</td>
      <td>${esc(i.category) || '-'}</td>
      <td>${esc(i.location) || '-'}</td>
      <td>${esc(i.description)}</td>
      <td>${esc(i.suggestion) || '-'}</td>
      <td>${issueTag(i.status)}</td>
      <td>${esc(i.resolveNote) || '-'}</td>
    </tr>`).join('')
  const now = new Date()
  const dateStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  const conclusion = drawingTag(detail.status) === '通过'
    ? '经审查，该图纸审查通过。' : drawingTag(detail.status) === '驳回'
    ? '经审查，该图纸驳回，退回设计单位整改。' : '该图纸审查中，请逐条核实处理审查问题。'

  return `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>图纸审查报告 ${esc(detail.drawingCode)}</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: "Microsoft YaHei", "PingFang SC", Arial, sans-serif; color:#222; margin:0; padding:28px; font-size:13px; line-height:1.7; }
  .head { text-align:center; border-bottom:2px solid #303133; padding-bottom:14px; margin-bottom:18px; }
  .head h1 { margin:0 0 6px; font-size:22px; letter-spacing:2px; }
  .head .sub { color:#909399; font-size:12px; }
  h2 { font-size:15px; margin:22px 0 10px; padding-left:8px; border-left:4px solid #409eff; }
  table { width:100%; border-collapse:collapse; margin-top:6px; }
  th, td { border:1px solid #dcdfe6; padding:7px 9px; text-align:left; vertical-align:top; }
  th { background:#f5f7fa; font-weight:600; white-space:nowrap; }
  .info td:nth-child(odd) { background:#fafafa; font-weight:600; width:15%; white-space:nowrap; }
  .lv-high { color:#c45656; font-weight:600; }
  .lv-medium { color:#e6a23c; font-weight:600; }
  .lv-low { color:#909399; font-weight:600; }
  .ai { background:#f8fbff; border:1px solid #d9ecff; border-left:4px solid #409eff; padding:10px 14px; white-space:pre-wrap; }
  .stats { display:flex; gap:14px; flex-wrap:wrap; margin-top:6px; }
  .stats span { background:#f5f7fa; border-radius:4px; padding:6px 14px; }
  .stats b { font-size:16px; margin-right:4px; }
  .warn { color:#c45656; }
  .sign { margin-top:34px; display:flex; justify-content:space-between; gap:24px; flex-wrap:wrap; }
  .sign div { flex:1 1 220px; }
  .sign .line { border-bottom:1px solid #909399; height:34px; }
  .foot { margin-top:28px; color:#e6a23c; font-size:12px; text-align:center; }
  .toolbar { text-align:right; margin-bottom:14px; }
  .toolbar button { font-size:13px; padding:6px 16px; border:1px solid #dcdfe6; background:#fff; border-radius:4px; cursor:pointer; margin-left:8px; }
  .toolbar button:hover { border-color:#409eff; color:#409eff; }
  @media print { .toolbar { display:none; } body { padding:12px; } }
</style></head>
<body>
  <div class="toolbar">
    <button onclick="window.print()">打印</button>
    <button onclick="downloadReport()">下载报告</button>
  </div>
  <div class="head">
    <h1>图纸审查报告</h1>
    <div class="sub">审查单号：${esc(detail.drawingCode)} ｜ 生成时间：${dateStr}</div>
  </div>

  <h2>一、图纸信息</h2>
  <table class="info">
    <tr><td>项目</td><td>${esc(detail.projectName) || '-'}</td><td>专业</td><td>${discTag(detail.discipline)}</td></tr>
    <tr><td>图纸标题</td><td>${esc(detail.title)}</td><td>图版/版本</td><td>${esc(detail.drawingVersion) || '-'}</td></tr>
    <tr><td>阶段</td><td>${stageTag(detail.stage)}</td><td>设计单位</td><td>${esc(detail.designUnit) || '-'}</td></tr>
    <tr><td>文件</td><td>${esc(detail.fileName)}</td><td>审查状态</td><td>${drawingTag(detail.status)}</td></tr>
  </table>

  <h2>二、AI 预审意见</h2>
  <div class="ai">${esc(detail.aiNote) || '（未执行 AI 预审）'}</div>

  <h2>三、审查问题清单（共 ${list.length} 条）</h2>
  <div class="stats">
    <span><b>${high}</b>严重</span>
    <span><b class="warn">${open}</b>待解决</span>
    <span><b>${resolved}</b>已解决</span>
    <span><b>${dismissed}</b>无需处理</span>
  </div>
  <table>
    <thead><tr>
      <th>#</th><th>来源</th><th>严重度</th><th>分类</th><th>位置</th><th>问题描述</th><th>修改建议</th><th>状态</th><th>解决说明</th>
    </tr></thead>
    <tbody>${rows || '<tr><td colspan="9" style="text-align:center;color:#909399">暂无审查问题</td></tr>'}</tbody>
  </table>

  <h2>四、审查结论</h2>
  <p>${conclusion}</p>

  <h2>五、签署</h2>
  <div class="sign">
    <div>审查人：<div class="line"></div>日期：<div class="line"></div></div>
    <div>设计单位（整改确认）：<div class="line"></div>日期：<div class="line"></div></div>
  </div>

  <div class="foot">🛡 ${esc(advisory) || 'AI 预审仅提示、不代审，最终审查结论由人工确认。'}</div>
  <script>
    function downloadReport(){
      var blob = new Blob([document.documentElement.outerHTML], { type:'text/html;charset=utf-8' });
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = '审查报告_${esc(detail.drawingCode)}.html';
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
    }
  </script>
</body></html>`
}
