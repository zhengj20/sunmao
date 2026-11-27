<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="toolbar-title">专项计划（按责任条线分组）</span>
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="reload">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目查看专项计划" :image-size="80" />

    <template v-else>
      <el-tabs v-model="specialty" @tab-change="loadList">
        <el-tab-pane v-for="s in SPECIALTIES" :key="s" :label="s" :name="s" />
      </el-tabs>

      <el-card shadow="never" class="table-card">
        <div class="card-head">
          <span class="card-title">{{ specialty }}条线 · 任务清单（{{ total }}）</span>
          <span class="hint">复用 /tasks/page?specialty= 按条线过滤，红黄绿灯与主项计划一致。</span>
        </div>
        <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="该条线暂无任务">
          <el-table-column prop="taskCode" label="编码" width="130" show-overflow-tooltip />
          <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ color: row.isMilestone ? '#c45656' : '' }">{{ row.isMilestone ? '◆ ' : '' }}{{ row.taskName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="级别" width="80" align="center">
            <template #default="{ row }"><el-tag :type="row.isMilestone ? 'danger' : 'info'" size="small">{{ row.level || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="计划起止" width="185" align="center">
            <template #default="{ row }"><span class="nowrap">{{ fmtDate(row.planStart) }} ~ {{ fmtDate(row.planEnd) }}</span></template>
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
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, taskSpecialtyPage,
  SPECIALTIES, TASK_STATUS, ALERT_STATUS, statusMeta, alertMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, TASK_STATUS)
const alert = s => alertMeta(s)

const projects = ref([])
const projectId = ref(null)
const specialty = ref('设计')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20 })

const loadList = async () => {
  if (!projectId.value) { rows.value = []; total.value = 0; return }
  loading.value = true
  try {
    const page = await taskSpecialtyPage({
      current: query.current, size: query.size, projectId: projectId.value, specialty: specialty.value
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const reload = () => { query.current = 1; loadList() }

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    loadList()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar-title { font-weight: 700; color: #0c447c; font-size: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.hint { font-size: 12px; color: #909399; }
.dim { color: #909399; }
.nowrap { white-space: nowrap; }
</style>
