<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="toolbar-title">里程碑管理（拿地 / 开工 / 正负零 / 预售 / 开盘 / 竣备 / 交付）</span>
        <div class="actions">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目查看里程碑" :image-size="80" />

    <template v-else>
      <el-row :gutter="12" class="stat-row">
        <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">里程碑总数</div><div class="value">{{ achievement.total ?? 0 }}</div></el-card></el-col>
        <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">已完成</div><div class="value" style="color:#67c23a">{{ achievement.completed ?? 0 }}</div></el-card></el-col>
        <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">进行中</div><div class="value" style="color:#e6a23c">{{ achievement.inProgress ?? 0 }}</div></el-card></el-col>
        <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">滞后</div><div class="value" style="color:#c45656">{{ achievement.delayed ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">达成率</div><div class="value">{{ Number(achievement.achievementRate) || 0 }}%</div></el-card></el-col>
      </el-row>

      <el-card shadow="never" class="list-card">
        <div class="card-head"><span class="card-title">里程碑台账</span></div>
        <el-table v-loading="loading" :data="ledger" border stripe size="small" empty-text="暂无里程碑">
          <el-table-column prop="taskCode" label="编码" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.taskCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="taskName" label="里程碑" min-width="160" show-overflow-tooltip />
          <el-table-column label="计划起止" width="185" align="center">
            <template #default="{ row }"><span class="nowrap">{{ fmtDate(row.planStart) }} ~ {{ fmtDate(row.planEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="基线结束" width="110" align="center">
            <template #default="{ row }"><span class="dim">{{ fmtDate(row.baselineEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="实际起止" width="185" align="center">
            <template #default="{ row }"><span class="nowrap dim">{{ fmtDate(row.actualStart) }} ~ {{ fmtDate(row.actualEnd) }}</span></template>
          </el-table-column>
          <el-table-column label="进度" width="90" align="center">
            <template #default="{ row }"><span class="num">{{ Number(row.progressPct) || 0 }}%</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="预警" width="80" align="center">
            <template #default="{ row }"><el-tag :type="alert(row.alertStatus)[1]" size="small">{{ alert(row.alertStatus)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="滞后(天)" width="90" align="center">
            <template #default="{ row }">
              <span v-if="Number(row.overdueDays) > 0" style="color:#c45656;font-weight:700">{{ row.overdueDays }}</span>
              <span v-else class="dim">0</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <div class="card-head"><span class="card-title">延期原因归类（滞后里程碑）</span></div>
        <el-table v-if="delayCatRows.length" :data="delayCatRows" border stripe size="small">
          <el-table-column prop="category" label="原因归类" min-width="160" align="center" />
          <el-table-column prop="count" label="数量" width="120" align="center" />
          <el-table-column label="占比" min-width="220" align="center">
            <template #default="{ row }"><el-progress :percentage="row.pct" :stroke-width="12" /></template>
          </el-table-column>
        </el-table>
        <div v-else class="hint" style="margin-top:8px">无滞后里程碑，暂无延期原因归类</div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, milestoneLedger, milestoneAchievement,
  TASK_STATUS, ALERT_STATUS, statusMeta, alertMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, TASK_STATUS)
const alert = s => alertMeta(s)

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const ledger = ref([])
const achievement = ref({})

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [l, a] = await Promise.all([
      milestoneLedger(projectId.value).catch(() => []),
      milestoneAchievement(projectId.value).catch(() => ({}))
    ])
    ledger.value = l || []
    achievement.value = a || {}
  } finally {
    loading.value = false
  }
}

const delayCatRows = computed(() => {
  const map = achievement.value.delayedByCategory || {}
  const entries = Object.entries(map)
  const total = entries.reduce((s, [, v]) => s + Number(v || 0), 0)
  return entries
    .map(([category, count]) => ({ category, count: Number(count) || 0, pct: total ? Math.round((Number(count) / total) * 100) : 0 }))
    .sort((x, y) => y.count - x.count)
})

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar-title { font-weight: 700; color: #0c447c; font-size: 14px; }
.actions { display: flex; gap: 8px; align-items: center; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #185fa5; }
.stat-card .label { font-size: 12.5px; color: #6b7280; }
.list-card { margin-bottom: 12px; }
.card-head { margin-bottom: 10px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.dim { color: #909399; }
.nowrap { white-space: nowrap; }
.hint { font-size: 12px; color: #909399; }
</style>
