<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable clearable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.meetingType" placeholder="类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="v in Object.keys(MEETING_TYPE)" :key="v" :label="MEETING_TYPE[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="会议号" clearable style="width: 150px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div>
          <el-button type="warning" @click="onOverdue">超期提醒</el-button>
          <el-button type="primary" @click="openForm">新增会议</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="暂无会议">
        <el-table-column prop="meetingNo" label="会议号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.meetingNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }"><el-tag :type="typeMeta(row.meetingType)[1]" size="small">{{ typeMeta(row.meetingType)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="会议日期" width="120" align="center">
          <template #default="{ row }">{{ fmtDate(row.meetingDate) }}</template>
        </el-table-column>
        <el-table-column prop="agenda" label="议题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.agenda || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTasks(row)">决议任务</el-button>
            <el-button link type="primary" size="small" @click="openForm(row)">补录任务</el-button>
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

    <!-- 新增会议 -->
    <el-dialog v-model="formVisible" :title="form.id ? '补录决议任务' : '新增会议'" width="560px">
      <el-form v-if="!form.id" label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.meetingType" style="width: 100%">
            <el-option v-for="v in Object.keys(MEETING_TYPE)" :key="v" :label="MEETING_TYPE[v][0]" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="会议日期">
          <el-date-picker v-model="form.meetingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="议题">
          <el-input v-model="form.agenda" type="textarea" :rows="2" placeholder="会议议题" />
        </el-form-item>
        <el-form-item label="纪要">
          <el-input v-model="form.minutes" type="textarea" :rows="3" placeholder="会议纪要" />
        </el-form-item>
      </el-form>
      <el-form v-else label-width="100px">
        <el-form-item label="决议任务" required>
          <el-input v-model="taskForm.taskDesc" type="textarea" :rows="2" placeholder="决议事项描述" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="taskForm.respUserName" placeholder="默认当前登录人" />
        </el-form-item>
        <el-form-item label="截止日期" required>
          <el-date-picker v-model="taskForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 决议任务 -->
    <el-dialog v-model="tasksVisible" :title="`决议任务（${tasksMeeting?.meetingNo || ''}）`" width="760px">
      <div class="task-toolbar">
        <span class="hint">超期(PENDING 且已过截止日)自动标红；闭环后可标记完成。</span>
        <el-button type="primary" size="small" @click="openTaskForm()">添加任务</el-button>
      </div>
      <el-table v-loading="tasksLoading" :data="tasks" border stripe size="small" empty-text="暂无决议任务">
        <el-table-column prop="taskDesc" label="决议事项" min-width="220" show-overflow-tooltip />
        <el-table-column prop="respUserName" label="责任人" width="110" align="center">
          <template #default="{ row }">{{ row.respUserName || '-' }}</template>
        </el-table-column>
        <el-table-column label="截止日期" width="120" align="center">
          <template #default="{ row }">
            <span :style="isOverdue(row) ? 'color:#c45656;font-weight:700' : ''">{{ fmtDate(row.dueDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="tmeta(row.status)[1]" size="small">{{ tmeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'DONE'" link type="success" size="small" @click="onDone(row)">完成</el-button>
            <span v-else class="hint">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="tasksVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 超期清单 -->
    <el-dialog v-model="overdueVisible" title="超期决议任务清单" width="720px">
      <el-table v-if="overdue.length" :data="overdue" border stripe size="small">
        <el-table-column prop="taskDesc" label="决议事项" min-width="220" show-overflow-tooltip />
        <el-table-column prop="respUserName" label="责任人" width="110" align="center" />
        <el-table-column label="截止日期" width="120" align="center">
          <template #default="{ row }"><span style="color:#c45656;font-weight:700">{{ fmtDate(row.dueDate) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="tmeta(row.status)[1]" size="small">{{ tmeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="无超期决议任务" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions,
  meetingPage, meetingCreate, meetingDetail, meetingAddTask, meetingTaskDone, meetingOverdue,
  MEETING_TYPE, MEETING_STATUS, MEETING_TASK_STATUS, statusMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, MEETING_STATUS)
const typeMeta = s => statusMeta(s, MEETING_TYPE)
const tmeta = s => statusMeta(s, MEETING_TASK_STATUS)
const isOverdue = row => row.status !== 'DONE' && row.dueDate && new Date(row.dueDate) < new Date(new Date().toDateString())

const projects = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, meetingType: null, kw: '', current: 1, size: 20 })
const saving = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const page = await meetingPage({
      current: query.current, size: query.size,
      projectId: query.projectId || undefined, meetingType: query.meetingType || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.meetingType = null; query.kw = ''; onSearch() }

// ===== 新增会议 / 补录任务 =====
const formVisible = ref(false)
const form = reactive({ id: null, projectId: null, meetingType: 'REGULAR', meetingDate: null, agenda: '', minutes: '' })
const openForm = row => {
  if (row) {
    Object.assign(form, { id: row.id, projectId: row.projectId, meetingType: row.meetingType, meetingDate: row.meetingDate, agenda: row.agenda, minutes: row.minutes })
  } else {
    Object.assign(form, { id: null, projectId: query.projectId, meetingType: 'REGULAR', meetingDate: null, agenda: '', minutes: '' })
  }
  formVisible.value = true
}
const onSave = async () => {
  if (form.id) {
    if (!taskForm.taskDesc) return ElMessage.warning('请填写决议任务')
    if (!taskForm.dueDate) return ElMessage.warning('请选择截止日期')
    saving.value = true
    try {
      await meetingAddTask(form.id, { taskDesc: taskForm.taskDesc, respUserName: taskForm.respUserName || undefined, dueDate: taskForm.dueDate })
      ElMessage.success('决议任务已添加')
      formVisible.value = false
      if (tasksMeeting.value?.id === form.id) loadTasks(form.id)
    } finally {
      saving.value = false
    }
    return
  }
  if (!form.projectId) return ElMessage.warning('请选择项目')
  saving.value = true
  try {
    await meetingCreate({
      projectId: form.projectId, meetingType: form.meetingType,
      meetingDate: form.meetingDate || undefined, agenda: form.agenda || undefined, minutes: form.minutes || undefined
    })
    ElMessage.success('会议已创建')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ===== 决议任务 =====
const tasksVisible = ref(false)
const tasksLoading = ref(false)
const tasks = ref([])
const tasksMeeting = ref(null)
const loadTasks = async id => {
  tasksLoading.value = true
  try {
    const m = await meetingDetail(id).catch(() => null)
    tasks.value = m?.tasks || []
  } finally {
    tasksLoading.value = false
  }
}
const openTasks = row => {
  tasksMeeting.value = row
  loadTasks(row.id)
  tasksVisible.value = true
}
const onDone = async row => {
  await ElMessageBox.confirm('确认标记该决议任务为已完成？', '提示', { type: 'success' })
  await meetingTaskDone(row.id)
  ElMessage.success('已标记完成')
  loadTasks(tasksMeeting.value.id)
}

// 添加任务表单
const taskForm = reactive({ taskDesc: '', respUserName: '', dueDate: null })
const openTaskForm = () => {
  Object.assign(taskForm, { taskDesc: '', respUserName: '', dueDate: null })
  formVisible.value = true
}

// ===== 超期提醒 =====
const overdueVisible = ref(false)
const overdue = ref([])
const onOverdue = async () => {
  overdue.value = (await meetingOverdue().catch(() => [])) || []
  overdueVisible.value = true
  if (overdue.value.length) ElMessage.warning(`发现 ${overdue.value.length} 条超期决议任务`)
  else ElMessage.success('无超期决议任务')
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
.task-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.hint { font-size: 12px; color: #909399; }
</style>
