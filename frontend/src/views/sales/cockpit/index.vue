<template>
  <div class="page-container cockpit">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        <span class="hint">销售驾驶舱：核心经营指标 · 销售/回款趋势 · 渠道 · 产品去化 · 顾问业绩 · 蓄客漏斗</span>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- KPI 卡片 -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">签约额</div><div class="value">{{ wan(kpi.saleAmount) }}<span class="unit">万</span></div><div class="sub">{{ kpi.saleCount ?? 0 }} 套</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">回款额</div><div class="value" style="color:#67c23a">{{ wan(kpi.receiptAmount) }}<span class="unit">万</span></div><div class="sub">{{ kpi.receiptCount ?? 0 }} 笔</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">回款率</div><div class="value" style="color:#185fa5">{{ kpi.collectionRate ?? 0 }}%</div><div class="sub">实收/应收</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">去化率</div><div class="value">{{ kpi.turnoverRate ?? 0 }}%</div><div class="sub">已售货值/总货值</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">来访量</div><div class="value">{{ kpi.visitCount ?? 0 }}</div><div class="sub">案场来访</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">转化率</div><div class="value" style="color:#0c447c">{{ kpi.contractRate ?? 0 }}%</div><div class="sub">来访→签约</div></el-card></el-col>
      </el-row>

      <!-- 销售趋势 + 蓄客漏斗 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><span class="card-title">签约 / 回款月度趋势（万元）</span></template>
            <ChartBox v-if="trendPeriods.length" :option="trendOption" height="300px" />
            <el-empty v-else description="暂无签约/回款数据" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never">
            <template #header><span class="card-title">蓄客漏斗（来访→认购→签约→回款）</span></template>
            <ChartBox v-if="funnel.length" :option="funnelOption" height="300px" />
            <el-empty v-else description="暂无蓄客数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 渠道 + 产品去化 + 顾问 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">渠道来访结构</span></template>
            <ChartBox v-if="channel.visitByChannel?.length" :option="channelVisitOption" height="260px" />
            <el-empty v-else description="暂无来访渠道" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">产品去化（户型 · 套数）</span></template>
            <ChartBox v-if="layout.length" :option="layoutOption" height="260px" />
            <el-empty v-else description="暂无房源" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">TOP 置业顾问（签约金额）</span></template>
            <el-table v-if="topAdvisors.length" :data="topAdvisors" size="small" empty-text="暂无顾问业绩">
              <el-table-column label="#" width="40" align="center"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column>
              <el-table-column prop="agentName" label="顾问" min-width="90" show-overflow-tooltip />
              <el-table-column label="签约(万)" width="90" align="right"><template #default="{ row }"><span class="num">{{ wan(row.conAmount) }}</span></template></el-table-column>
            </el-table>
            <el-empty v-else description="暂无顾问业绩" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { wan } from '../../../utils/format'
import { fetchProjectOptions, dashboardCockpit } from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const BLUE = '#185fa5'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const data = ref({})

const kpi = computed(() => data.value.kpi || {})
const funnel = computed(() => data.value.funnel || [])
const channel = computed(() => data.value.channel || {})
const layout = computed(() => data.value.layout || [])
const topAdvisors = computed(() => (data.value.topAdvisors || []).slice(0, 6))

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    data.value = (await dashboardCockpit(projectId.value)) || {}
  } catch (e) {
    data.value = {}
  } finally {
    loading.value = false
  }
}

// 趋势：合并签约/回款月份
const trendPeriods = computed(() => {
  const s = new Set()
  ;(data.value.contractTrend || []).forEach(t => s.add(t.period))
  ;(data.value.receiptTrend || []).forEach(t => s.add(t.period))
  return [...s].sort()
})
const trendOption = computed(() => {
  const periods = trendPeriods.value
  const cMap = Object.fromEntries((data.value.contractTrend || []).map(t => [t.period, Number(t.amt) / 10000]))
  const rMap = Object.fromEntries((data.value.receiptTrend || []).map(t => [t.period, Number(t.amt) / 10000]))
  return {
    color: [BLUE, '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toFixed(1) + ' 万元' },
    legend: { data: ['签约金额', '回款金额'], bottom: 0 },
    grid: { left: 60, right: 24, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: periods, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: '万元' },
    series: [
      { name: '签约金额', type: 'line', smooth: true, data: periods.map(p => cMap[p] || 0), areaStyle: { opacity: 0.08 } },
      { name: '回款金额', type: 'line', smooth: true, data: periods.map(p => rMap[p] || 0), areaStyle: { opacity: 0.08 } }
    ]
  }
})

const funnelOption = computed(() => ({
  color: ['#5687ba', '#185fa5', '#0c447c', '#67c23a'],
  tooltip: { trigger: 'item', formatter: '{b}: {c}' },
  series: [{
    type: 'funnel', left: '8%', right: '8%', top: 16, bottom: 16,
    min: 0, minSize: '30%', maxSize: '100%', sort: 'descending', gap: 3,
    label: { show: true, position: 'inside', formatter: '{b}\n{c}' },
    itemStyle: { borderColor: '#fff', borderWidth: 1 },
    data: funnel.value.map(s => ({ name: s.name, value: Number(s.count) }))
  }]
}))

const channelVisitOption = computed(() => ({
  color: ['#185fa5', '#5687ba', '#8aadd2', '#67c23a', '#e6a23c', '#909399'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} 组 ({d}%)' },
  legend: { bottom: 0, type: 'scroll' },
  series: [{
    type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'],
    itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
    label: { show: false },
    data: (channel.value.visitByChannel || []).map(c => ({ name: c.channel, value: Number(c.cnt) }))
  }]
}))

const layoutOption = computed(() => {
  const names = layout.value.map(l => l.layout)
  return {
    color: ['#185fa5', '#8aadd2'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['总套数', '已售'], bottom: 0 },
    grid: { left: 44, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 5 ? 20 : 0 } },
    yAxis: { type: 'value' },
    series: [
      { name: '总套数', type: 'bar', barWidth: 18, data: layout.value.map(l => Number(l.total)) },
      { name: '已售', type: 'bar', barWidth: 18, data: layout.value.map(l => Number(l.sold)), itemStyle: { color: '#0c447c' } }
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
.cockpit { max-width: 1560px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.hint { font-size: 12px; color: #909399; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; font-weight: 700; color: #1f2937; font-variant-numeric: tabular-nums; }
.stat-card .unit { font-size: 12px; color: #909399; margin-left: 2px; font-weight: 400; }
.stat-card .sub { font-size: 12px; color: #909399; margin-top: 4px; }
.section-row { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.num { font-variant-numeric: tabular-nums; }
</style>
