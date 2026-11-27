<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">工作流编排</h2>
        <p class="page-sub">多步骤任务编排 — 环节接力（上游产物注入下游）· 人工审批门 · 定时触发 · 整合交付</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新建工作流</el-button>
    </div>

    <!-- 工作流卡片 -->
    <div v-loading="loading" class="wf-grid">
      <div v-for="wf in workflows" :key="wf.workflow.id" class="wf-card">
        <div class="wf-head">
          <div>
            <div class="wf-name">{{ wf.workflow.name }}
              <el-tag v-if="wf.workflow.cronExpr" size="small" type="primary" effect="plain" style="margin-left:6px">定时 {{ wf.workflow.cronExpr }} · 下次 {{ fmt(wf.workflow.nextRunAt) }}</el-tag>
              <el-tag v-if="wf.workflow.kbDocIds" size="small" type="success" effect="plain" style="margin-left:6px">📚 知识库绑定</el-tag>
            </div>
            <div class="wf-desc">{{ wf.workflow.description || '' }}</div>
          </div>
          <el-tag :type="wfRunTag(wf.lastRun?.status)" size="small">
            {{ wfRunText(wf.lastRun?.status) }}
          </el-tag>
        </div>

        <div class="wf-steps">
          <div v-for="(s, i) in wf.steps" :key="i" class="wf-step-item">
            <span class="step-num">{{ i + 1 }}</span>
            <div class="step-info">
              <div class="step-name">{{ s.step?.name || s.taskName }}
                <em v-if="s.step?.requiresApproval === 1" class="step-approve-flag" title="该环节需人工审批">需审批</em>
                <em v-if="s.step?.usePrev === 0" class="step-norelay-flag" title="不接力上一环节产物">不接力</em>
              </div>
              <div class="step-meta">{{ s.employeeName }} · {{ s.skillName }}</div>
            </div>
            <span v-if="i < wf.steps.length - 1" class="step-arrow">→</span>
          </div>
        </div>

        <div class="wf-foot">
          <span class="muted small">{{ wf.steps.length }} 步</span>
          <div class="wf-actions">
            <el-button size="small" type="primary" @click="onRun(wf)">执行</el-button>
            <el-button size="small" @click="openEdit(wf)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="onDelete(wf)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!workflows.length && !loading" description="暂无工作流，点击「新建」创建" />
    </div>

    <!-- 人工审批中心 -->
    <el-card shadow="never" class="approve-card" v-if="approvals.length">
      <template #header>
        <div class="card-head">
          <span class="card-title">⚖ 待人工审批（{{ approvals.length }}）</span>
          <span class="muted small">工作流执行到「需审批」环节时暂停，批准继续 / 驳回终止</span>
        </div>
      </template>
      <div v-for="a in approvals" :key="a.approval.id" class="approve-item">
        <div class="approve-main">
          <b>{{ a.wfName }}</b>
          <el-tag size="small" type="warning" effect="plain">环节 {{ a.approval.stepOrder }}：{{ a.approval.stepName }}</el-tag>
          <span v-if="a.employeeName" class="muted small">{{ a.employeeName }}</span>
          <span class="muted small">{{ fmt(a.approval.createdAt) }}</span>
        </div>
        <div class="approve-actions">
          <el-input v-model="a.comment" placeholder="审批意见（可选）" size="small" style="width: 200px" />
          <el-button size="small" type="success" :loading="a._deciding" @click="onDecide(a, 'approve')">批准继续</el-button>
          <el-button size="small" type="danger" plain :loading="a._deciding" @click="onDecide(a, 'reject')">驳回终止</el-button>
        </div>
      </div>
    </el-card>

    <!-- 执行记录 -->
    <el-card shadow="never" class="run-card" v-if="runs.length">
      <template #header>
        <div class="card-head">
          <span class="card-title">最近执行记录</span>
          <el-button link @click="loadRuns">刷新</el-button>
        </div>
      </template>
      <el-table :data="runs" size="small" stripe>
        <el-table-column prop="wfName" label="工作流" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="wfRunTag(row.status)" size="small">{{ wfRunText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="130">
          <template #default="{ row }">
            {{ row.currentStep }}/{{ row.totalSteps }}
            <el-tag v-if="row.failedSteps > 0" type="warning" size="small" style="margin-left: 4px">{{ row.failedSteps }}步失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="160">
          <template #default="{ row }">{{ fmt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ duration(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewRun(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="formDlg" :title="editingId ? '编辑工作流' : '新建工作流'" width="720px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：月度经营分析" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="工作流用途说明" />
        </el-form-item>
        <el-form-item label="定时触发">
          <el-input v-model="form.cronExpr" placeholder="留空=仅手动；CRON 6 段含秒，如 0 45 8 * * 1-5" style="width: 320px" />
          <div class="cron-presets">
            <el-tag v-for="p in cronPresets" :key="p.v" class="cron-preset" @click="form.cronExpr = p.v">{{ p.t }}</el-tag>
          </div>
          <div class="muted small" style="margin-top: 6px">停机错过 15 分钟内的槽位会自动补跑，更早的只顺延不补跑</div>
        </el-form-item>
        <el-form-item label="知识库绑定">
          <el-select v-model="form.kbDocIdsArr" multiple filterable style="width: 100%"
                     placeholder="选择该工作流全部环节固定引用的知识库文档（执行时整篇注入提示词）">
            <el-option v-for="d in kbDocs" :key="d.id" :label="d.title" :value="d.id">
              <span>{{ d.title }}</span>
              <span class="opt-cat">{{ d.category || '未分类' }}</span>
            </el-option>
          </el-select>
          <div v-if="!kbDocs.length" class="muted small">暂无知识库文档，请先到「知识库」添加</div>
        </el-form-item>
        <el-form-item label="步骤">
          <div class="steps-editor">
            <div v-for="(s, i) in form.steps" :key="i" class="step-row">
              <span class="step-order">{{ i + 1 }}</span>
              <el-select v-model="s.taskId" placeholder="选择任务" style="flex:1">
                <el-option v-for="t in allTasks" :key="t.id" :label="t.employeeName + ' · ' + t.name" :value="t.id" />
              </el-select>
              <el-select v-model="s.onFail" style="width:110px">
                <el-option label="停止" value="STOP" />
                <el-option label="跳过" value="SKIP" />
                <el-option label="继续" value="CONTINUE" />
              </el-select>
              <el-checkbox v-model="s.usePrev" :true-label="1" :false-label="0" title="接力上一环节产物注入本环节提示词">接力</el-checkbox>
              <el-checkbox v-model="s.requiresApproval" :true-label="1" :false-label="0" title="执行到此暂停，等待人工批准/驳回">需审批</el-checkbox>
              <el-button circle type="danger" plain size="small" @click="form.steps.splice(i, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="form.steps.push({ taskId: null, onFail: 'STOP', name: '', usePrev: 1, requiresApproval: 0 })">+ 添加步骤</el-button>
            <div class="muted small" style="margin-top: 6px">
              「接力」：上一环节完整产出自动注入本环节 Prompt（首环节无上游）；「需审批」：执行到该环节暂停，审批中心批准/驳回
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行详情 -->
    <el-dialog v-model="runDlg" title="工作流执行详情" width="640px">
      <div v-if="runDetail" class="run-detail">
        <div class="rd-head">
          <span class="rd-name">{{ runDetail.wfName }}</span>
          <el-tag :type="wfRunTag(runDetail.status)" size="small">{{ wfRunText(runDetail.status) }}</el-tag>
          <el-tag v-if="runDetail.failedSteps > 0" type="warning" size="small">{{ runDetail.failedSteps }} 步失败（按策略跳过/继续）</el-tag>
          <span class="muted small">{{ fmt(runDetail.startedAt) }}</span>
        </div>
        <div v-if="runDetail.status === 'PAUSED'" class="rd-paused">
          ⚖ 工作流已暂停于需审批环节 — 请到上方「待人工审批」区域批准继续或驳回终止
        </div>
        <div v-if="runDetail.error" class="rd-err">错误：{{ runDetail.error }}</div>
        <div class="rd-steps">
          <div v-for="(s, i) in parsedSteps" :key="i" class="rd-step" :class="{ clickable: s.taskRunId }" @click="s.taskRunId && viewStepRun(s)">
            <span class="rd-step-dot" :class="s.status === 'DONE' ? 'ok' : 'fail'" />
            <div class="rd-step-body">
              <div class="rd-step-name">{{ s.order }}. {{ s.name }}</div>
              <div class="rd-step-status" :class="s.status === 'DONE' ? 'ok-text' : 'fail-text'">{{ s.status }}<template v-if="s.error"> · {{ s.error }}</template></div>
            </div>
            <el-button v-if="s.taskRunId" link type="primary" size="small" @click.stop="viewStepRun(s)">过程</el-button>
          </div>
        </div>
        <div v-if="runDetail.output" class="rd-output">
          <div class="rd-output-h">📦 整合交付（全环节完成后自动生成）</div>
          <pre>{{ runDetail.output }}</pre>
        </div>
      </div>
    </el-dialog>

    <!-- 步骤执行过程（联动任务执行记录） -->
    <el-dialog v-model="stepDlg" :title="stepRun ? `步骤执行过程 · ${stepRun.taskName}` : '步骤执行过程'" width="640px" append-to-body>
      <div v-if="stepRun">
        <div class="rd-head">
          <el-tag :type="stepRun.status === 'DONE' ? 'success' : stepRun.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
            {{ stepRun.status === 'DONE' ? '已完成' : stepRun.status === 'RUNNING' ? '执行中' : '失败' }}
          </el-tag>
          <el-tag v-if="stepRun.aiMode" size="small" :type="stepRun.aiMode === 'LLM' ? 'primary' : 'info'">
            {{ stepRun.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}
          </el-tag>
          <el-tag v-if="(stepRun.tokenIn || stepRun.tokenOut)" size="small" effect="plain">
            Tokens {{ stepRun.tokenIn || 0 }}/{{ stepRun.tokenOut || 0 }}
          </el-tag>
          <span class="muted small">{{ fmt(stepRun.startedAt) }}</span>
        </div>
        <RunSteps :steps="stepRun.steps" />
        <div v-if="stepRun.output" class="rd-output">
          <div class="rd-output-h">执行结果</div>
          <pre>{{ stepRun.output }}</pre>
        </div>
        <div v-if="stepRun.error" class="rd-err">错误：{{ stepRun.error }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import RunSteps from '../../../components/RunSteps.vue'
import { wfBoard, wfCreate, wfUpdate, wfDelete, wfRun, wfRuns, wfRunDetail, wfApprovals, wfDecide, taskList, empList, runDetail as taskRunDetail } from '../../../api/digital'
import { kbPage } from '../../../api/ai'

const loading = ref(false)
const workflows = ref([])
const runs = ref([])
const allTasks = ref([])

// 运行状态展示
const wfRunTag = s => s === 'DONE' ? 'success' : s === 'RUNNING' ? 'primary' : s === 'PAUSED' ? 'warning' : s === 'REJECTED' ? 'info' : 'danger'
const wfRunText = s => ({ DONE: '已完成', RUNNING: '运行中', PAUSED: '待审批', FAIL: '失败', REJECTED: '已驳回' }[s] || '未运行')

const cronPresets = [
  { t: '每天 08:30', v: '0 30 8 * * *' },
  { t: '工作日 08:45', v: '0 45 8 * * 1-5' },
  { t: '每周一 09:00', v: '0 0 9 * * 1' },
  { t: '每月 1 日 08:00', v: '0 0 8 1 * *' }
]

// 待人工审批
const approvals = ref([])
const loadApprovals = async () => {
  try { approvals.value = (await wfApprovals()) || [] } catch (e) { approvals.value = [] }
}
const onDecide = async (a, decision) => {
  a._deciding = true
  try {
    await wfDecide(a.approval.id, decision, a.comment || '')
    ElMessage.success(decision === 'approve' ? '已批准，工作流继续执行' : '已驳回，工作流终止')
    loadApprovals()
    setTimeout(() => { loadRuns() }, 1500)
  } finally { a._deciding = false }
}

// form
const formDlg = ref(false)
const editingId = ref(null)
const saving = ref(false)
const form = ref({ name: '', description: '', cronExpr: '', kbDocIdsArr: [], steps: [] })

// 知识库文档（工作流级绑定选择用）
const kbDocs = ref([])
const loadKbDocs = async () => {
  try {
    const r = await kbPage({ current: 1, size: 100 })
    kbDocs.value = r?.records || r?.list || (Array.isArray(r) ? r : [])
  } catch (e) { kbDocs.value = [] }
}

// run detail
const runDlg = ref(false)
const runDetail = ref(null)

const fmt = s => s ? String(s).replace('T', ' ').slice(5, 16) : '—'
const duration = r => {
  if (!r.startedAt || !r.finishedAt) return '—'
  const ms = new Date(r.finishedAt) - new Date(r.startedAt)
  return ms >= 1000 ? (ms / 1000).toFixed(1) + 's' : ms + 'ms'
}

const load = async () => {
  loading.value = true
  try {
    workflows.value = (await wfBoard()) || []
  } finally { loading.value = false }
}

const loadRuns = async () => {
  runs.value = (await wfRuns({ current: 1, size: 10 })) || []
  startRunPoll() // 有运行中记录时保持轮询
}

// 运行中自动轮询（含待审批列表）
const hasActiveRun = computed(() =>
  runs.value.some(r => r.status === 'RUNNING' || r.status === 'PAUSED') || approvals.value.length > 0)
let runTimer = null
const startRunPoll = () => {
  if (runTimer) clearInterval(runTimer)
  runTimer = null
  if (hasActiveRun.value) runTimer = setInterval(() => { loadRuns(); loadApprovals() }, 2500)
}
const stopRunPoll = () => { if (runTimer) { clearInterval(runTimer); runTimer = null } }

const loadTasks = async () => {
  const tasks = (await taskList({ current: 1, size: 100 })) || []
  const emps = (await empList()) || []
  const empMap = Object.fromEntries(emps.map(e => [e.id, e.name]))
  allTasks.value = tasks.map(t => ({ ...t, employeeName: empMap[t.employeeId] || '' }))
}

const openCreate = () => {
  editingId.value = null
  form.value = { name: '', description: '', cronExpr: '', kbDocIdsArr: [], steps: [{ taskId: null, onFail: 'STOP', name: '', usePrev: 1, requiresApproval: 0 }] }
  formDlg.value = true
}

const openEdit = (wf) => {
  editingId.value = wf.workflow.id
  form.value = {
    name: wf.workflow.name,
    description: wf.workflow.description || '',
    cronExpr: wf.workflow.cronExpr || '',
    kbDocIdsArr: wf.workflow.kbDocIds ? wf.workflow.kbDocIds.split(',').map(x => Number(x.trim())).filter(Boolean) : [],
    steps: wf.steps.map(s => ({
      taskId: s.step.taskId, onFail: s.step.onFail || 'STOP', name: s.step.name || '',
      usePrev: s.step.usePrev === 0 ? 0 : 1,
      requiresApproval: s.step.requiresApproval === 1 ? 1 : 0
    }))
  }
  formDlg.value = true
}

const onDelete = async (wf) => {
  await ElMessageBox.confirm(`确认删除工作流「${wf.workflow.name}」？`, '提示', { type: 'warning' })
  await wfDelete(wf.workflow.id)
  ElMessage.success('已删除')
  load()
}

const onSave = async () => {
  if (!form.value.name?.trim()) return ElMessage.warning('请输入名称')
  const validSteps = form.value.steps.filter(s => s.taskId)
  if (!validSteps.length) return ElMessage.warning('请至少添加一个有效步骤')
  saving.value = true
  try {
    const payload = {
      workflow: {
        name: form.value.name, description: form.value.description,
        cronExpr: (form.value.cronExpr || '').trim() || null,
        kbDocIds: form.value.kbDocIdsArr?.length ? form.value.kbDocIdsArr.join(',') : null
      },
      steps: validSteps.map(s => ({
        taskId: s.taskId, onFail: s.onFail, name: s.name || '',
        usePrev: s.usePrev === 0 ? 0 : 1,
        requiresApproval: s.requiresApproval === 1 ? 1 : 0
      }))
    }
    if (editingId.value) {
      await wfUpdate(editingId.value, payload)
    } else {
      await wfCreate(payload)
    }
    ElMessage.success('已保存')
    formDlg.value = false
    load()
  } finally { saving.value = false }
}

const onRun = async (wf) => {
  const runId = await wfRun(wf.workflow.id)
  ElMessage.success(`已触发执行，runId=${runId}`)
  setTimeout(loadRuns, 2000)
}

const viewRun = async (row) => {
  runDetail.value = await wfRunDetail(row.id)
  runDlg.value = true
}

// 步骤执行过程联动
const parsedSteps = computed(() => {
  try { return runDetail.value?.steps ? JSON.parse(runDetail.value.steps) : [] } catch (e) { return [] }
})
const stepDlg = ref(false)
const stepRun = ref(null)
const viewStepRun = async (s) => {
  if (!s.taskRunId) return
  try {
    stepRun.value = (await taskRunDetail(s.taskRunId)) || null
    stepDlg.value = true
  } catch (e) {
    ElMessage.error('加载步骤执行过程失败')
  }
}

onMounted(async () => {
  await Promise.all([load(), loadRuns(), loadTasks(), loadApprovals(), loadKbDocs()])
})
onBeforeUnmount(stopRunPoll)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.page-title { font-size: 20px; font-weight: 700; color: #0c447c; margin: 0; }
.page-sub { font-size: 13px; color: #6b7280; margin: 4px 0 0; }
.wf-grid { display: flex; flex-direction: column; gap: 14px; margin-bottom: 20px; }
.wf-card {
  background: #fff; border: 1px solid #e8edf3; border-radius: 12px; padding: 18px 20px;
  transition: box-shadow .15s;
}
.wf-card:hover { box-shadow: 0 4px 16px rgba(12,72,124,.08); }
.wf-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.wf-name { font-size: 16px; font-weight: 700; color: #1f2937; }
.wf-desc { font-size: 12.5px; color: #6b7280; margin-top: 4px; }
.wf-steps { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 14px; }
.wf-step-item { display: flex; align-items: center; gap: 8px; }
.step-num {
  width: 22px; height: 22px; border-radius: 50%; background: #185fa5; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0;
}
.step-info { min-width: 0; }
.step-name { font-size: 13px; font-weight: 600; color: #1f2937; }
.step-meta { font-size: 11px; color: #6b7280; }
.step-arrow { color: #9ca3af; font-size: 16px; margin: 0 4px; }
.wf-foot { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #9ca3af; }
.small { font-size: 12px; }
.wf-actions { display: flex; gap: 6px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.run-card { margin-bottom: 12px; }

.steps-editor { width: 100%; }
.step-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.step-order { font-size: 13px; font-weight: 700; color: #185fa5; width: 20px; text-align: center; }

.run-detail { padding: 4px 0; }
.rd-head { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.rd-name { font-size: 15px; font-weight: 700; color: #1f2937; }
.rd-steps { display: flex; flex-direction: column; gap: 10px; }
.rd-step { display: flex; align-items: center; gap: 10px; }
.rd-step.clickable { cursor: pointer; }
.rd-step.clickable:hover .rd-step-name { color: #185fa5; text-decoration: underline; }
.rd-step-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.rd-step-dot.ok { background: #67c23a; }
.rd-step-dot.fail { background: #f56c6c; }
.rd-step-body { flex: 1; }
.rd-step-name { font-size: 13px; color: #1f2937; }
.rd-step-status { font-size: 11px; margin-top: 2px; }
.ok-text { color: #67c23a; }
.fail-text { color: #f56c6c; }
.rd-output { margin-top: 14px; border: 1px solid #e0eaf3; border-radius: 10px; overflow: hidden; background: #fbfdff; }
.rd-output-h { padding: 8px 12px; font-size: 12.5px; font-weight: 600; color: #0c447c; background: #eef5fb; border-bottom: 1px solid #e0eaf3; }
.rd-output pre {
  margin: 0; padding: 12px 14px; font-size: 12.5px; line-height: 1.75;
  white-space: pre-wrap; word-break: break-all; color: #33445a;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  max-height: 280px; overflow: auto;
}
.rd-err { margin-top: 10px; color: #c45656; font-size: 12.5px; }
.rd-paused {
  margin-bottom: 12px; padding: 9px 12px; font-size: 12.5px;
  color: #b76e00; background: #fdf4e3; border: 1px solid #ecd39c; border-radius: 8px;
}

/* 审批中心 */
.approve-card { margin-bottom: 14px; }
.approve-item {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap;
  padding: 10px 12px; border: 1px solid #f0e2c8; background: #fffdf7; border-radius: 10px; margin-bottom: 8px;
}
.approve-item:last-child { margin-bottom: 0; }
.approve-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.approve-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* 步骤标记 */
.step-approve-flag {
  font-style: normal; font-size: 10px; color: #b76e00; background: #fdf4e3;
  border: 1px solid #ecd39c; border-radius: 4px; padding: 0 4px; margin-left: 6px; vertical-align: 1px;
}
.step-norelay-flag {
  font-style: normal; font-size: 10px; color: #7b8a9c; background: #f2f5f8;
  border: 1px solid #dfe6ee; border-radius: 4px; padding: 0 4px; margin-left: 4px; vertical-align: 1px;
}

/* CRON 预设 */
.cron-presets { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.cron-preset { cursor: pointer; user-select: none; }
.cron-preset:hover { color: #185fa5; border-color: #185fa5; }
</style>
