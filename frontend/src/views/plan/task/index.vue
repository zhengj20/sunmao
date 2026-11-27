<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable clearable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="v in Object.keys(TASK_STATUS)" :key="v" :label="TASK_STATUS[v][0]" :value="v" />
          </el-select>
          <el-select v-model="query.alertStatus" placeholder="预警" clearable style="width: 110px" @change="onSearch">
            <el-option v-for="v in Object.keys(ALERT_STATUS)" :key="v" :label="ALERT_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="名称/编码" clearable style="width: 150px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div>
          <el-button type="primary" @click="openGenerate()">从模板生成</el-button>
          <el-button @click="onRecompute" :disabled="!query.projectId">重算预警</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!query.projectId" description="请选择项目查看主项计划" :image-size="80" />

    <template v-else>
      <el-card shadow="never" class="table-card">
        <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="暂无任务，可点击「从模板生成」">
          <el-table-column prop="taskCode" label="编码" width="130" show-overflow-tooltip />
          <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ paddingLeft: (indent(row.level)) + 'px', color: row.isMilestone ? '#c45656' : '' }">
                {{ row.isMilestone ? '◆ ' : '' }}{{ row.taskName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="级别" width="80" align="center">
            <template #default="{ row }"><el-tag :type="row.isMilestone ? 'danger' : 'info'" size="small">{{ row.level || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="specialty" label="条线" width="80" align="center">
            <template #default="{ row }">{{ row.specialty || '-' }}</template>
          </el-table-column>
          <el-table-column label="计划起止" width="185" align="center">
            <template #default="{ row }"><span class="nowrap">{{ fmtDate(row.planStart) }} ~ {{ fmtDate(row.planEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="基线" width="185" align="center">
            <template #default="{ row }"><span class="nowrap dim">{{ fmtDate(row.baselineStart) }} ~ {{ fmtDate(row.baselineEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="实际" width="185" align="center">
            <template #default="{ row }"><span class="nowrap dim">{{ fmtDate(row.actualStart) }} ~ {{ fmtDate(row.actualEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="进度" width="90" align="center">
            <template #default="{ row }"><span class="num">{{ Number(row.progressPct) || 0 }}%</span></template>
          </el-table-column>
          <el-table-column prop="respUserName" label="责任人" width="100" align="center">
            <template #default="{ row }">{{ row.respUserName || '-' }}</template>
          </el-table-column>
          <el-table-column label="预警" width="80" align="center">
            <template #default="{ row }"><el-tag :type="alert(row.alertStatus)[1]" size="small">{{ alert(row.alertStatus)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openProgress(row)">进度填报</el-button>
              <el-button link type="primary" size="small" @click="openSchedule(row)">改时间</el-button>
              <el-button link type="warning" size="small" @click="openImpact(row)">影响</el-button>
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
    </template>

    <!-- 从模板生成 -->
    <el-dialog v-model="genVisible" title="从模板生成项目 WBS" width="480px">
      <el-alert type="warning" :closable="false" style="margin-bottom:14px"
        title="生成后该项目将写入标准节点为任务（里程碑节点自动标记）。项目已存在任务时不可重复生成。" />
      <el-form label-width="90px">
        <el-form-item label="项目">
          <el-select v-model="gen.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板" required>
          <el-select v-model="gen.templateId" placeholder="选择标准模板" filterable style="width: 100%">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始日期">
          <el-date-picker v-model="gen.startDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 进度填报 -->
    <el-dialog v-model="progressVisible" title="进度填报" width="480px">
      <div class="hint" style="margin-bottom:12px">任务：{{ progressTask?.taskName }}（{{ progressTask?.taskCode }}）</div>
      <el-form label-width="100px">
        <el-form-item label="完成进度" required>
          <el-slider v-model="progress.progressPct" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="实际开始">
          <el-date-picker v-model="progress.actualStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实际完成">
          <el-date-picker v-model="progress.actualEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="提示"><div class="hint">进度=100% 自动置「已完成」；修改实际开始将校验前置依赖(R2)。</div></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveProgress">提交</el-button>
      </template>
    </el-dialog>

    <!-- 改计划时间 -->
    <el-dialog v-model="scheduleVisible" title="修改计划时间" width="480px">
      <div class="hint" style="margin-bottom:12px">任务：{{ scheduleTask?.taskName }}</div>
      <el-form label-width="90px">
        <el-form-item label="计划开始">
          <el-date-picker v-model="schedule.planStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="schedule.planEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="提示"><div class="hint">里程碑节点不可直接改时间，须走计划调整(R1)。</div></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveSchedule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 滞后连锁影响 -->
    <el-dialog v-model="impactVisible" title="滞后连锁影响分析" width="720px">
      <div v-if="impact" class="impact-head">
        <span class="card-title">任务：{{ impact.taskName }}</span>
        <el-tag v-if="impact.overdueDays > 0" type="danger">滞后 {{ impact.overdueDays }} 天</el-tag>
        <el-tag type="warning">影响下游 {{ impact.downstreamCount }} 个节点</el-tag>
      </div>
      <el-table v-if="impact" :data="impact.downstream" border stripe size="small" empty-text="无下游受影响节点" max-height="360">
        <el-table-column prop="taskCode" label="编码" width="120" />
        <el-table-column prop="taskName" label="下游任务" min-width="180" show-overflow-tooltip />
        <el-table-column prop="specialty" label="条线" width="80" align="center" />
        <el-table-column label="计划结束" width="110" align="center"><template #default="{ row }">{{ fmtDate(row.planEnd) }}</template></el-table-column>
        <el-table-column label="进度" width="80" align="center"><template #default="{ row }">{{ Number(row.progressPct) || 0 }}%</template></el-table-column>
        <el-table-column prop="respUserName" label="责任人" width="100" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions,
  taskPage, taskGenerate, taskReportProgress, taskUpdateSchedule,
  templatePage, alertRecompute, alertImpact,
  TASK_STATUS, ALERT_STATUS, statusMeta, alertMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, TASK_STATUS)
const alert = s => alertMeta(s)
const indent = level => ({ '里程碑': 0, '一级': 0, '二级': 16, '三级': 32 }[level] ?? 0)

const projects = ref([])
const templates = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, alertStatus: null, kw: '', current: 1, size: 20 })
const saving = ref(false)

const loadList = async () => {
  if (!query.projectId) { rows.value = []; total.value = 0; return }
  loading.value = true
  try {
    const page = await taskPage({
      current: query.current, size: query.size, projectId: query.projectId,
      status: query.status || undefined, alertStatus: query.alertStatus || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.alertStatus = null; query.kw = ''; onSearch() }

const loadTemplates = async () => {
  const page = await templatePage({ current: 1, size: 100 }).catch(() => null)
  templates.value = page?.records || []
}

// ===== 从模板生成 =====
const genVisible = ref(false)
const gen = reactive({ templateId: null, startDate: null })
const openGenerate = () => {
  gen.templateId = null; gen.startDate = null
  gen.projectId = query.projectId
  genVisible.value = true
}
const onGenerate = async () => {
  if (!gen.projectId) return ElMessage.warning('请选择项目')
  if (!gen.templateId) return ElMessage.warning('请选择模板')
  saving.value = true
  try {
    const n = await taskGenerate(gen.templateId, gen.projectId, gen.startDate || undefined)
    ElMessage.success(`已生成 ${n} 个计划任务`)
    genVisible.value = false
    if (gen.projectId === query.projectId) loadList()
  } finally {
    saving.value = false
  }
}

const onRecompute = async () => {
  const n = await alertRecompute(query.projectId)
  ElMessage.success(`预警已重算，变更 ${n} 项`)
  loadList()
}

// ===== 进度填报 =====
const progressVisible = ref(false)
const progressTask = ref(null)
const progress = reactive({ progressPct: 0, actualStart: null, actualEnd: null })
const openProgress = row => {
  progressTask.value = row
  Object.assign(progress, { progressPct: Number(row.progressPct) || 0, actualStart: row.actualStart || null, actualEnd: row.actualEnd || null })
  progressVisible.value = true
}
const onSaveProgress = async () => {
  const v = Number(progress.progressPct)
  if (v < 0 || v > 100) return ElMessage.warning('进度须在 0~100 之间')
  saving.value = true
  try {
    await taskReportProgress(progressTask.value.id, { progressPct: v, actualStart: progress.actualStart || undefined, actualEnd: progress.actualEnd || undefined })
    ElMessage.success('进度已填报，预警已重算')
    progressVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ===== 改时间 =====
const scheduleVisible = ref(false)
const scheduleTask = ref(null)
const schedule = reactive({ planStart: null, planEnd: null })
const openSchedule = row => {
  scheduleTask.value = row
  Object.assign(schedule, { planStart: row.planStart || null, planEnd: row.planEnd || null })
  scheduleVisible.value = true
}
const onSaveSchedule = async () => {
  if (!schedule.planStart && !schedule.planEnd) return ElMessage.warning('请选择新的计划时间')
  saving.value = true
  try {
    await taskUpdateSchedule(scheduleTask.value.id, { planStart: schedule.planStart || undefined, planEnd: schedule.planEnd || undefined })
    ElMessage.success('计划时间已更新')
    scheduleVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ===== 影响分析 =====
const impactVisible = ref(false)
const impact = ref(null)
const openImpact = async row => {
  impact.value = await alertImpact(row.id).catch(() => null)
  impactVisible.value = true
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList()
  }
  loadTemplates()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.dim { color: #909399; }
.nowrap { white-space: nowrap; }
.hint { font-size: 12px; color: #909399; }
.impact-head { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.card-title { font-weight: 700; color: #0c447c; }
</style>
