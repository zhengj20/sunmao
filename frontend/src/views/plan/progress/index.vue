<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable clearable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="query.reportWeek" placeholder="报告周(如 2026-W35)" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm">填报周报</el-button>
      </div>
    </el-card>

    <!-- 周汇总 -->
    <el-card v-if="projectId" shadow="never" class="stat-card-row">
      <div class="weekly-head"><span class="card-title">周汇总（最近 {{ weekly.length }} 周）</span></div>
      <el-table v-if="weekly.length" :data="weekly" border stripe size="small" style="margin-top:10px">
        <el-table-column prop="reportWeek" label="周" width="120" align="center" />
        <el-table-column prop="taskCount" label="填报任务数" width="120" align="center" />
        <el-table-column label="平均进度" width="200" align="center">
          <template #default="{ row }"><el-progress :percentage="Number(row.avgProgress) || 0" :stroke-width="12" /></template>
        </el-table-column>
      </el-table>
      <div v-else class="hint" style="margin-top:8px">暂无周汇总数据</div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="card-head"><span class="card-title">进度周报明细</span></div>
      <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="暂无填报记录">
        <el-table-column prop="reportWeek" label="报告周" width="110" align="center">
          <template #default="{ row }">{{ row.reportWeek || '-' }}</template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || ('任务#' + row.taskId) }}</template>
        </el-table-column>
        <el-table-column label="进度" width="150" align="center">
          <template #default="{ row }"><el-progress :percentage="Number(row.progressPct) || 0" :stroke-width="12" /></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="实际开始" width="110" align="center"><template #default="{ row }">{{ fmtDate(row.actualStart) }}</template></el-table-column>
        <el-table-column label="实际完成" width="110" align="center"><template #default="{ row }">{{ fmtDate(row.actualEnd) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="填报时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
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

    <!-- 填报周报 -->
    <el-dialog v-model="formVisible" title="填报进度周报" width="520px">
      <el-form label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务" required>
          <el-select v-model="form.taskId" placeholder="选择任务" filterable style="width: 100%" :loading="tasksLoading">
            <el-option v-for="t in tasks" :key="t.id" :label="`${t.taskCode} ${t.taskName}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="完成进度" required>
          <el-slider v-model="form.progressPct" :min="0" :max="100" show-input />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="实际开始">
              <el-date-picker v-model="form.actualStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际完成">
              <el-date-picker v-model="form.actualEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="本周进展说明" />
        </el-form-item>
        <el-form-item label="提示"><div class="hint">提交后回写任务进度/实际日期并触发预警重算与前置依赖校验(R2)。</div></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fmtDate, fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, taskList,
  progressPage, progressRecord, progressWeekly,
  PROGRESS_STATUS, statusMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, PROGRESS_STATUS)
const projects = ref([])
const tasks = ref([])
const tasksLoading = ref(false)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const weekly = ref([])
const query = reactive({ projectId: null, reportWeek: '', current: 1, size: 20 })
const saving = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const page = await progressPage({
      current: query.current, size: query.size,
      projectId: query.projectId || undefined, reportWeek: query.reportWeek || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const loadWeekly = async () => {
  if (!query.projectId) { weekly.value = []; return }
  weekly.value = (await progressWeekly(query.projectId, 8).catch(() => [])) || []
}
const onSearch = () => { query.current = 1; loadList(); loadWeekly() }
const onReset = () => { query.reportWeek = ''; onSearch() }

const loadTasks = async () => {
  if (!query.projectId) { tasks.value = []; return }
  tasksLoading.value = true
  try {
    tasks.value = (await taskList(query.projectId).catch(() => [])) || []
  } finally {
    tasksLoading.value = false
  }
}

const formVisible = ref(false)
const form = reactive({ projectId: null, taskId: null, progressPct: 50, actualStart: null, actualEnd: null, description: '' })
const openForm = () => {
  if (!query.projectId) return ElMessage.warning('请先选择项目')
  Object.assign(form, { projectId: query.projectId, taskId: null, progressPct: 50, actualStart: null, actualEnd: null, description: '' })
  loadTasks()
  formVisible.value = true
}
const onSave = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (!form.taskId) return ElMessage.warning('请选择任务')
  const v = Number(form.progressPct)
  if (v < 0 || v > 100) return ElMessage.warning('进度须在 0~100 之间')
  saving.value = true
  try {
    await progressRecord({
      projectId: form.projectId, taskId: form.taskId, progressPct: v,
      actualStart: form.actualStart || undefined, actualEnd: form.actualEnd || undefined,
      description: form.description || undefined
    })
    ElMessage.success('周报已提交，任务进度已回写')
    formVisible.value = false
    loadList(); loadWeekly()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList(); loadWeekly(); loadTasks()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.card-head { margin-bottom: 10px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.weekly-head { margin-bottom: 4px; }
.stat-card-row { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; }
</style>
