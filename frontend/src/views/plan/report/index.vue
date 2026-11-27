<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="toolbar-title">计划报表</span>
        <div class="actions">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目查看计划报表" :image-size="80" />

    <template v-else>
      <el-tabs v-model="tab" class="inline-tabs">
        <el-tab-pane label="甘特图" name="gantt" />
        <el-tab-pane label="里程碑达成" name="milestone" />
        <el-tab-pane label="执行排名" name="ranking" />
      </el-tabs>

      <!-- 甘特图 -->
      <div v-show="tab === 'gantt'">
        <el-card shadow="never">
          <div class="card-head">
            <span class="card-title">WBS 甘特图（计划区间 + 进度 + 预警色）</span>
            <div class="legend">
              <span class="lg"><i style="background:#67c23a"></i>正常</span>
              <span class="lg"><i style="background:#e6a23c"></i>预警</span>
              <span class="lg"><i style="background:#c45656"></i>滞后</span>
              <span class="lg dim">深色段=已完成进度，浅色段=剩余计划</span>
            </div>
          </div>
          <ChartBox v-if="ganttData.length" :option="ganttOption" :height="Math.max(320, ganttData.length * 34) + 'px'" />
          <el-empty v-else description="暂无甘特图数据" :image-size="60" />
        </el-card>
      </div>

      <!-- 里程碑达成 -->
      <div v-show="tab === 'milestone'">
        <el-row :gutter="12" class="stat-row">
          <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">里程碑总数</div><div class="value">{{ msAch.total ?? 0 }}</div></el-card></el-col>
          <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">已完成</div><div class="value" style="color:#67c23a">{{ msAch.completed ?? 0 }}</div></el-card></el-col>
          <el-col :span="5"><el-card shadow="never" class="stat-card"><div class="label">滞后</div><div class="value" style="color:#c45656">{{ msAch.delayed ?? 0 }}</div></el-card></el-col>
          <el-col :span="9"><el-card shadow="never" class="stat-card"><div class="label">达成率（{{ msMonth }}）</div><div class="value" style="color:#185fa5">{{ Number(msAch.achievementRate) || 0 }}%</div></el-card></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">延期原因归类</span></template>
              <ChartBox v-if="delayCatOption && delayCatOption.series[0].data.length" :option="delayCatOption" height="300px" />
              <el-empty v-else description="无滞后里程碑" :image-size="50" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">里程碑台账</span></template>
              <el-table :data="msLedger" border stripe size="small" max-height="300" empty-text="暂无里程碑">
                <el-table-column prop="taskName" label="里程碑" min-width="120" show-overflow-tooltip />
                <el-table-column label="计划结束" width="100" align="center"><template #default="{ row }">{{ fmtDate(row.planEnd) }}</template></el-table-column>
                <el-table-column label="实际结束" width="100" align="center"><template #default="{ row }">{{ fmtDate(row.actualEnd) }}</template></el-table-column>
                <el-table-column label="状态" width="90" align="center"><template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template></el-table-column>
                <el-table-column label="滞后" width="70" align="center"><template #default="{ row }"><span :style="Number(row.overdueDays) > 0 ? 'color:#c45656;font-weight:700' : ''">{{ Number(row.overdueDays) || 0 }}</span></template></el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 执行排名 -->
      <div v-show="tab === 'ranking'">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">各单位红黄绿任务分布</span></template>
              <ChartBox v-if="ranking.length" :option="rankBarOption" :height="Math.max(260, ranking.length * 42) + 'px'" />
              <el-empty v-else description="暂无排名数据" :image-size="50" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">执行排名（按完成率）</span></template>
              <el-table :data="ranking" border stripe size="small" empty-text="暂无排名数据">
                <el-table-column prop="rank" label="排名" width="60" align="center" />
                <el-table-column prop="orgName" label="责任单位" min-width="140" show-overflow-tooltip />
                <el-table-column prop="total" label="任务数" width="80" align="center" />
                <el-table-column prop="green" label="绿" width="60" align="center"><template #default="{ row }"><span style="color:#67c23a">{{ row.green }}</span></template></el-table-column>
                <el-table-column prop="yellow" label="黄" width="60" align="center"><template #default="{ row }"><span style="color:#e6a23c">{{ row.yellow }}</span></template></el-table-column>
                <el-table-column prop="red" label="红" width="60" align="center"><template #default="{ row }"><span style="color:#c45656;font-weight:700">{{ row.red }}</span></template></el-table-column>
                <el-table-column prop="completed" label="完成" width="70" align="center" />
                <el-table-column label="完成率" width="130" align="center"><template #default="{ row }"><el-progress :percentage="Number(row.onTimeRate) || 0" :stroke-width="12" /></template></el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import * as echarts from 'echarts'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, reportGantt, reportMilestone, reportRanking,
  TASK_STATUS, statusMeta
} from '../../../api/plan'
import ChartBox from '../../../components/ChartBox.vue'

const ALERT_COLOR = { GREEN: '#67c23a', YELLOW: '#e6a23c', RED: '#c45656' }
const meta = s => statusMeta(s, TASK_STATUS)
const ts = v => (v ? new Date(v).getTime() : null)

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const tab = ref('gantt')

const ganttData = ref([])
const msLedger = ref([])
const msAch = ref({})
const msMonth = ref('')
const ranking = ref([])

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [g, m, r] = await Promise.all([
      reportGantt(projectId.value).catch(() => []),
      reportMilestone(projectId.value).catch(() => ({})),
      reportRanking(projectId.value).catch(() => [])
    ])
    ganttData.value = g || []
    msLedger.value = m?.ledger || []
    msAch.value = m?.achievement || {}
    msMonth.value = m?.month || ''
    ranking.value = r || []
  } finally {
    loading.value = false
  }
}

// ===== 甘特图（echarts custom）=====
const ganttOption = computed(() => {
  const rows = ganttData.value
  const cats = rows.map(r => (r.isMilestone ? '◆ ' : '') + (r.taskName || r.taskCode || '-'))
  const data = rows.map((r, i) => {
    const s = ts(r.planStart), e = ts(r.planEnd)
    return [i, s != null && e != null ? s : 0, s != null && e != null ? e : 0, (Number(r.progressPct) || 0) / 100, ALERT_COLOR[r.alertStatus] || '#185fa5']
  }).filter(d => d[1] && d[2])
  return {
    grid: { left: 150, right: 30, top: 30, bottom: 40 },
    tooltip: {
      trigger: 'item',
      formatter: p => {
        const r = rows[p.data[0]] || {}
        return `${r.taskCode || ''} ${r.taskName || ''}<br/>计划：${fmtDate(r.planStart)} ~ ${fmtDate(r.planEnd)}<br/>进度：${Number(r.progressPct) || 0}%<br/>预警：${r.alertStatus || '-'}<br/>责任人：${r.respUserName || '-'}`
      }
    },
    xAxis: { type: 'time', axisLabel: { fontSize: 11 } },
    yAxis: { type: 'category', data: cats, inverse: true, axisLabel: { fontSize: 12, width: 130, overflow: 'truncate' } },
    series: [{
      type: 'custom',
      encode: { x: [1, 2], y: 0 },
      data,
      renderItem: (params, api) => {
        const catIndex = api.value(0)
        const startV = api.value(1)
        const endV = api.value(2)
        const prog = Math.min(Math.max(api.value(3) || 0, 0), 1)
        const color = api.value(4) || '#185fa5'
        const start = api.coord([startV, catIndex])
        const end = api.coord([endV, catIndex])
        const progEnd = api.coord([startV + (endV - startV) * prog, catIndex])
        const height = api.size([0, 1])[1] * 0.5
        const clip = params.coordSys
        const clipFn = (x, y, w, h) => echarts.graphic.clipRectByRect({ x, y, width: w, height: h },
          { x: clip.x, y: clip.y, width: clip.width, height: clip.height })
        const base = clipFn(start[0], start[1] - height / 2, end[0] - start[0], height)
        const progShape = clipFn(start[0], start[1] - height / 2, Math.max(progEnd[0] - start[0], 0), height)
        return {
          type: 'group',
          children: [
            { type: 'rect', shape: base, style: { fill: color, opacity: 0.3 } },
            { type: 'rect', shape: progShape, style: { fill: color } }
          ]
        }
      }
    }]
  }
})

// ===== 延期原因归类 =====
const delayCatOption = computed(() => {
  const map = msAch.value.delayedByCategory || {}
  const entries = Object.entries(map).map(([name, value]) => ({ name, value: Number(value) || 0 }))
  if (!entries.length) return null
  return {
    color: ['#c45656', '#e6a23c', '#5687ba', '#185fa5', '#8aadd2'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      name: '延期原因', type: 'pie', radius: ['40%', '68%'], center: ['50%', '46%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}' },
      data: entries
    }]
  }
})

// ===== 执行排名柱状图 =====
const rankBarOption = computed(() => {
  const rows = ranking.value
  const cats = rows.map(r => r.orgName || '-')
  return {
    color: ['#67c23a', '#e6a23c', '#c45656'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0 },
    grid: { left: 110, right: 24, top: 20, bottom: 40 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: cats, inverse: true, axisLabel: { fontSize: 12, width: 95, overflow: 'truncate' } },
    series: [
      { name: '绿', type: 'bar', stack: 't', data: rows.map(r => Number(r.green) || 0), barWidth: 18 },
      { name: '黄', type: 'bar', stack: 't', data: rows.map(r => Number(r.yellow) || 0) },
      { name: '红', type: 'bar', stack: 't', data: rows.map(r => Number(r.red) || 0) }
    ]
  }
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
.inline-tabs { margin-bottom: 8px; }
.inline-tabs :deep(.el-tabs__item) { font-size: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.lg { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 4px; }
.lg i { display: inline-block; width: 12px; height: 12px; border-radius: 2px; }
.dim { color: #909399; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #185fa5; }
.stat-card .label { font-size: 12.5px; color: #6b7280; }
</style>
