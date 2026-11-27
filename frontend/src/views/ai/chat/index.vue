<template>
  <div class="page-container chat-page">
    <!-- 会话列表 -->
    <el-card shadow="never" class="session-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">我的会话</span>
          <el-button type="primary" size="small" @click="onNewSession">+ 新建</el-button>
        </div>
      </template>
      <div v-loading="sessLoading" class="session-list">
        <div
          v-for="s in sessions" :key="s.sessionId"
          class="session-item" :class="{ active: s.sessionId === activeSessionId }"
          @click="onSelectSession(s)"
        >
          <div class="session-title">{{ s.title || '新会话' }}</div>
          <div class="session-foot">
            <span class="muted">{{ fmtTime(s.createdAt) }}</span>
            <el-icon class="del" @click.stop="onDeleteSession(s)"><Delete /></el-icon>
          </div>
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话，点击「新建」" :image-size="50" />
      </div>
    </el-card>

    <!-- 消息流 -->
    <el-card shadow="never" class="msg-card">
      <div v-loading="msgLoading" ref="msgBox" class="msg-box">
        <el-empty v-if="!messages.length" description="输入问题，如「本月签约额」「回款率」「动态成本」" :image-size="70" />
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="bubble">
            <div class="msg-content">{{ m.content }}</div>
            <div v-if="m.metric && m.metric.matched" class="metric-chip">
              <el-tag size="small" effect="plain" type="success">命中指标</el-tag>
              <span class="metric-name">{{ m.metric.metricName }}</span>
              <span class="num metric-val">{{ m.metric.value }}{{ m.metric.unit }}</span>
              <span v-if="m.metric.caliber" class="muted">口径：{{ m.metric.caliber }}</span>
              <span v-if="m.metric.source" class="muted">来源：{{ m.metric.source }}</span>
            </div>
            <div v-if="m.role === 'ASSISTANT'" class="msg-foot">
              <el-tag :type="aiModeMeta(m.aiMode)[1]" size="small">{{ aiModeMeta(m.aiMode)[0] }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 模式选择 -->
      <div class="mode-bar">
        <el-radio-group v-model="mode">
          <el-radio-button value="METRIC">指标查询</el-radio-button>
          <el-radio-button value="KB">知识库</el-radio-button>
          <el-radio-button value="EMPLOYEE">数字员工</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 数字员工：任务选择 -->
      <div v-if="mode === 'EMPLOYEE'" v-loading="tasksLoading" class="task-bar">
        <span class="task-bar-label">执行任务</span>
        <el-select v-model="selectedTaskId" placeholder="请选择要执行的任务" clearable style="width: 360px">
          <el-option-group v-for="g in taskGroups" :key="g.label" :label="g.label">
            <el-option v-for="o in g.options" :key="o.id" :label="`${g.label} - ${o.name}`" :value="o.id" />
          </el-option-group>
        </el-select>
      </div>

      <!-- 输入区 -->
      <div class="input-bar">
        <el-input
          v-model="input" type="textarea" :rows="2" resize="none"
          :placeholder="placeholder"
          @keydown.enter.exact.prevent="onSend"
        />
        <el-button type="primary" :loading="sending" @click="onSend">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  chatSessions, chatCreateSession, chatMessages, chatSend, chatDeleteSession, aiModeMeta
} from '../../../api/ai'
import { taskList, empList } from '../../../api/digital'

const sessions = ref([])
const sessLoading = ref(false)
const activeSessionId = ref(null)
const messages = ref([])
const msgLoading = ref(false)
const sending = ref(false)
const input = ref('')
const msgBox = ref(null)

const mode = ref('METRIC')
const selectedTaskId = ref(null)
const tasks = ref([])
const employees = ref([])
const tasksLoaded = ref(false)
const tasksLoading = ref(false)

const placeholder = computed(() => {
  if (mode.value === 'KB') return '输入问题，如「折扣审批制度」「回款率口径」'
  if (mode.value === 'EMPLOYEE') return '选择任务后输入备注（可选），或直接发送执行'
  return '输入自然语言问题，如「本月签约额」「回款率」'
})

const taskGroups = computed(() => {
  const byEmp = new Map()
  for (const t of tasks.value) {
    const emp = employees.value.find(e => e.id === t.employeeId)
    const key = t.employeeId != null ? t.employeeId : 'none'
    const label = (emp && emp.name) || '未分配员工'
    let g = byEmp.get(key)
    if (!g) { g = { label, options: [] }; byEmp.set(key, g) }
    g.options.push({ id: t.id, name: t.name })
  }
  return [...byEmp.values()]
})

const loadTasks = async () => {
  if (tasksLoaded.value || tasksLoading.value) return
  tasksLoading.value = true
  try {
    const [t, e] = await Promise.all([taskList(), empList()])
    tasks.value = t || []
    employees.value = e || []
    tasksLoaded.value = true
  } catch (err) {
    /* 请求错误已由 request.js 提示 */
  } finally {
    tasksLoading.value = false
  }
}

watch(mode, m => { if (m === 'EMPLOYEE') loadTasks() })

const loadSessions = async () => {
  sessLoading.value = true
  try {
    sessions.value = (await chatSessions()) || []
  } finally {
    sessLoading.value = false
  }
}

const loadMessages = async sessionId => {
  msgLoading.value = true
  messages.value = []
  try {
    const list = (await chatMessages(sessionId)) || []
    messages.value = list.map(m => {
      let metric = null
      if (m.refs) {
        try {
          const r = typeof m.refs === 'string' ? JSON.parse(m.refs) : m.refs
          metric = {
            matched: r.matched, metricName: '', value: r.value, unit: '',
            caliber: r.source ? '' : '', source: r.source
          }
        } catch (e) { /* refs 非 JSON 时忽略 */ }
      }
      return { role: m.role, content: m.content, aiMode: m.aiMode, metric }
    })
    scrollToBottom()
  } finally {
    msgLoading.value = false
  }
}

const onSelectSession = s => {
  activeSessionId.value = s.sessionId
  loadMessages(s.sessionId)
}

const onNewSession = async () => {
  const s = await chatCreateSession()
  await loadSessions()
  activeSessionId.value = s.sessionId
  messages.value = []
  ElMessage.success('已创建新会话')
}

const onDeleteSession = async s => {
  await ElMessageBox.confirm(`确认删除会话「${s.title || '新会话'}」？`, '提示', { type: 'warning' })
  await chatDeleteSession(s.sessionId)
  if (activeSessionId.value === s.sessionId) {
    activeSessionId.value = null
    messages.value = []
  }
  loadSessions()
  ElMessage.success('已删除')
}

const ensureSession = async () => {
  if (activeSessionId.value) return activeSessionId.value
  const s = await chatCreateSession()
  await loadSessions()
  activeSessionId.value = s.sessionId
  return s.sessionId
}

const onSend = async () => {
  const q = input.value.trim()
  const isEmp = mode.value === 'EMPLOYEE'
  if (isEmp && !selectedTaskId.value) return ElMessage.warning('请选择要执行的任务')
  if (!q && !isEmp) return ElMessage.warning('请输入问题')
  input.value = ''
  sending.value = true
  try {
    const sessionId = await ensureSession()
    const finalQ = (isEmp && !q) ? '执行任务' : q
    const res = await chatSend(sessionId, finalQ, mode.value, isEmp ? selectedTaskId.value : null)
    const a = res.answer || {}
    messages.value.push({ role: 'USER', content: finalQ })
    messages.value.push({
      role: 'ASSISTANT',
      content: a.answer || '（无回答）',
      aiMode: a.aiMode,
      metric: {
        matched: a.matched, metricName: a.metricName || '',
        value: a.value, unit: a.unit || '', caliber: a.caliber || '', source: a.source || ''
      }
    })
    scrollToBottom()
  } catch (e) {
    /* 后端提示已由 request.js 展示 */
  } finally {
    sending.value = false
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length) onSelectSession(sessions.value[0])
})
</script>

<style scoped>
.chat-page { display: flex; gap: 12px; align-items: flex-start; }
.session-card { flex: 0 0 260px; }
.msg-card { flex: 1; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.session-list { max-height: 70vh; overflow-y: auto; }
.session-item { padding: 8px 10px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; }
.session-item:hover { background: #f0f5fa; }
.session-item.active { background: #eef5fb; border-color: #c3d6e8; }
.session-title { font-weight: 600; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 2px; }
.muted { color: #909399; font-size: 12px; }
.del { cursor: pointer; color: #c0c4cc; }
.del:hover { color: #f56c6c; }
.msg-box { height: 60vh; overflow-y: auto; padding: 4px; }
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row.USER { justify-content: flex-end; }
.msg-row.ASSISTANT { justify-content: flex-start; }
.bubble { max-width: 78%; padding: 8px 12px; border-radius: 10px; }
.msg-row.USER .bubble { background: #185fa5; color: #fff; }
.msg-row.ASSISTANT .bubble { background: #f0f5fa; color: #1f2937; }
.msg-content { line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.metric-chip { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.metric-name { font-weight: 600; }
.metric-val { font-weight: 700; color: #185fa5; }
.msg-foot { margin-top: 6px; }
.mode-bar { margin-top: 10px; }
.task-bar { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.task-bar-label { font-size: 13px; color: #606266; flex: 0 0 auto; }
.input-bar { display: flex; gap: 10px; align-items: flex-end; margin-top: 10px; }
.input-bar .el-textarea { flex: 1; }
</style>
