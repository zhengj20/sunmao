<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable clearable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(ADJUST_STATUS)" :key="v" :label="ADJUST_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="调整单号" clearable style="width: 150px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm">新建调整</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="暂无调整单">
        <el-table-column prop="adjustNo" label="调整单号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.adjustNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || ('任务#' + row.taskId) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="调整原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reasonCategory" label="原因归类" width="100" align="center">
          <template #default="{ row }"><el-tag size="small" type="info">{{ row.reasonCategory || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="原时间" width="175" align="center">
          <template #default="{ row }"><span class="old">{{ fmtDate(row.oldStart) }} ~ {{ fmtDate(row.oldEnd) }}</span></template>
        </el-table-column>
        <el-table-column label="新时间" width="175" align="center">
          <template #default="{ row }"><span class="new">{{ fmtDate(row.newStart) }} ~ {{ fmtDate(row.newEnd) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="primary" size="small" @click="onSubmit(row)">提交审批</el-button>
            <span v-else class="hint">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 新建调整 -->
    <el-dialog v-model="formVisible" title="新建计划调整单" width="560px">
      <el-form label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width: 100%" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务" required>
          <el-select v-model="form.taskId" placeholder="选择任务" filterable style="width: 100%" :loading="tasksLoading" @change="onTaskChange">
            <el-option v-for="t in tasks" :key="t.id" :label="`${t.taskCode} ${t.taskName}${t.isMilestone ? ' ◆里程碑' : ''}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="curTask" label="原计划">
          <div class="old-line">{{ fmtDate(curTask.planStart) }} ~ {{ fmtDate(curTask.planEnd) }}
            <el-tag v-if="curTask.isMilestone" type="danger" size="small" style="margin-left:6px">里程碑</el-tag>
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="新开始">
              <el-date-picker v-model="form.newStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="新结束">
              <el-date-picker v-model="form.newEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="diffDays !== null" label="时间差">
          <el-tag :type="diffDays > 0 ? 'danger' : (diffDays < 0 ? 'success' : 'info')" size="small">
            计划结束 {{ diffDays > 0 ? '+' : '' }}{{ diffDays }} 天（{{ diffDays > 0 ? '延后' : (diffDays < 0 ? '提前' : '不变') }}）
          </el-tag>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="请说明调整原因" />
        </el-form-item>
        <el-form-item :label="curTask?.isMilestone ? '原因归类*' : '原因归类'">
          <el-select v-model="form.reasonCategory" clearable style="width: 100%">
            <el-option v-for="c in REASON_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="curTask?.isMilestone" label="提示">
          <div class="hint">里程碑调整须填写延期原因归类(R1)，提交走 PLAN_ADJUST 审批。</div>
        </el-form-item>
        <el-form-item label="影响分析">
          <el-input v-model="form.impactAnalysis" type="textarea" :rows="2" placeholder="可选：对后续节点/成本的影响说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, taskList,
  adjustPage, adjustCreate, adjustSubmit,
  ADJUST_STATUS, REASON_CATEGORIES, statusMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, ADJUST_STATUS)

const projects = ref([])
const tasks = ref([])
const tasksLoading = ref(false)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, kw: '', current: 1, size: 20 })
const saving = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const page = await adjustPage({
      current: query.current, size: query.size,
      projectId: query.projectId || undefined, status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.kw = ''; onSearch() }

const loadTasks = async projectId => {
  if (!projectId) { tasks.value = []; return }
  tasksLoading.value = true
  try {
    tasks.value = (await taskList(projectId).catch(() => [])) || []
  } finally {
    tasksLoading.value = false
  }
}

const formVisible = ref(false)
const form = reactive({ projectId: null, taskId: null, newStart: null, newEnd: null, reason: '', reasonCategory: '', impactAnalysis: '' })
const curTask = computed(() => tasks.value.find(t => t.id === form.taskId) || null)
const diffDays = computed(() => {
  if (!curTask.value || !form.newEnd) return null
  const a = curTask.value.planEnd, b = form.newEnd
  if (!a || !b) return null
  return Math.round((new Date(b) - new Date(a)) / 86400000)
})
const onProjectChange = () => { form.taskId = null; loadTasks(form.projectId) }
const onTaskChange = id => {
  const t = tasks.value.find(x => x.id === id)
  if (t) Object.assign(form, { newStart: t.planStart, newEnd: t.planEnd })
}
const openForm = () => {
  if (!query.projectId) return ElMessage.warning('请先选择项目')
  Object.assign(form, { projectId: query.projectId, taskId: null, newStart: null, newEnd: null, reason: '', reasonCategory: '', impactAnalysis: '' })
  loadTasks(query.projectId)
  formVisible.value = true
}
const onSave = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (!form.taskId) return ElMessage.warning('请选择任务')
  if (!form.reason) return ElMessage.warning('请填写调整原因')
  if (!form.newStart && !form.newEnd) return ElMessage.warning('请填写新的计划时间')
  if (curTask.value?.isMilestone && !form.reasonCategory) return ElMessage.warning('里程碑调整须填写延期原因归类(R1)')
  saving.value = true
  try {
    await adjustCreate({
      taskId: form.taskId, reason: form.reason,
      reasonCategory: form.reasonCategory || undefined,
      newStart: form.newStart || undefined, newEnd: form.newEnd || undefined,
      impactAnalysis: form.impactAnalysis || undefined
    })
    ElMessage.success('调整单已登记（DRAFT）')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交调整单「${row.adjustNo || row.id}」进入 PLAN_ADJUST 审批？`, '提示', { type: 'warning' })
  await adjustSubmit(row.id)
  ElMessage.success('已提交审批')
  loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.old { color: #909399; text-decoration: line-through; }
.new { color: #c45656; font-weight: 600; }
.old-line { color: #909399; }
.hint { font-size: 12px; color: #909399; }
</style>
