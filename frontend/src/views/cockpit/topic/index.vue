<template>
  <div class="page-container cockpit-topic">
    <div class="topic-layout">
      <!-- 左侧专题切换 -->
      <div class="topic-nav">
        <div
          v-for="t in TOPICS"
          :key="t.value"
          class="topic-nav-item"
          :class="{ active: tab === t.value }"
          @click="switchTab(t.value)"
        >
          <span class="tn-dot" />
          {{ t.label }}
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="topic-body" v-loading="loading">
        <!-- 货值 -->
        <template v-if="tab === 'value' && data">
          <el-row :gutter="12" class="stat-row">
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">存销比（在手/已售 ×100%）</div><div class="value">{{ pct(data.stockSalesRatio) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">供货货值（万元）</div><div class="value">{{ wan(sumOf(data.distribution, 'totalValue')) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">签约月度数</div><div class="value">{{ (data.saleTrend || []).length }}</div></el-card></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">货值分布（业态）</span></template><ChartBox :option="valueDistOption" height="300px" /></el-card></el-col>
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">去化速度（月度签约）</span></template><ChartBox :option="saleTrendOption" height="300px" /></el-card></el-col>
          </el-row>
          <el-card shadow="never" class="list-card"><template #header><span class="card-title">货值分布明细</span></template>
            <el-table :data="data.distribution || []" border stripe size="small" empty-text="暂无数据">
              <el-table-column prop="productCategory" label="业态" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.productCategory || '-' }}</template></el-table-column>
              <el-table-column label="面积(㎡)" width="130" align="right"><template #default="{ row }"><span class="num">{{ money(row.area) }}</span></template></el-table-column>
              <el-table-column label="货值(万)" width="130" align="right"><template #default="{ row }"><span class="num">{{ wan(row.totalValue) }}</span></template></el-table-column>
            </el-table>
          </el-card>
        </template>

        <!-- 成本 -->
        <template v-if="tab === 'cost' && data">
          <el-row :gutter="12">
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">科目单方对比（金额 / 单方）</span></template><ChartBox :option="accountOption" height="320px" /></el-card></el-col>
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">变更率排行（确认变更/合同额）</span></template><ChartBox :option="changeOption" height="320px" /></el-card></el-col>
          </el-row>
          <el-card shadow="never" class="list-card"><template #header><span class="card-title">无效成本清单</span></template>
            <el-table :data="data.invalidCost || []" border stripe size="small" empty-text="暂无数据">
              <el-table-column prop="projectName" label="项目" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.projectName || '-' }}</template></el-table-column>
              <el-table-column prop="invalidType" label="无效类型" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.invalidType || '-' }}</template></el-table-column>
              <el-table-column label="金额(万)" width="130" align="right"><template #default="{ row }"><span class="num">{{ wan(row.amount) }}</span></template></el-table-column>
              <el-table-column label="发生日期" width="120" align="center"><template #default="{ row }">{{ fmtDate(row.occurredDate) }}</template></el-table-column>
            </el-table>
          </el-card>
        </template>

        <!-- 销售 -->
        <template v-if="tab === 'sales' && data">
          <el-row :gutter="12" class="stat-row">
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">成交均价(元)</div><div class="value">{{ money(data.priceAchievement?.avgContractPrice) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">表价均价(元)</div><div class="value">{{ money(data.priceAchievement?.avgTablePrice) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">价格实现率</div><div class="value value-blue">{{ pct(data.priceAchievement?.rate) }}</div></el-card></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">价格实现率（成交 vs 表价）</span></template><ChartBox :option="priceOption" height="300px" /></el-card></el-col>
            <el-col :span="12"><el-card shadow="never"><template #header><span class="card-title">渠道效力（按来源）</span></template><ChartBox :option="channelOption" height="300px" /></el-card></el-col>
          </el-row>
        </template>

        <!-- 招采 -->
        <template v-if="tab === 'proc' && data">
          <el-row :gutter="12">
            <el-col :span="14"><el-card shadow="never"><template #header><span class="card-title">定标价偏差（立项 vs 定标）</span></template><ChartBox :option="deviationOption" height="320px" /></el-card></el-col>
            <el-col :span="10"><el-card shadow="never"><template #header><span class="card-title">围串标统计（按规则/风险级）</span></template><ChartBox :option="collusionOption" height="320px" /></el-card></el-col>
          </el-row>
          <el-card shadow="never" class="list-card"><template #header><span class="card-title">定标偏差明细</span></template>
            <el-table :data="data.deviation || []" border stripe size="small" empty-text="暂无数据">
              <el-table-column prop="title" label="项目" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.title || '-' }}</template></el-table-column>
              <el-table-column label="立项(万)" width="120" align="right"><template #default="{ row }"><span class="num">{{ wan(row.budgetAmount) }}</span></template></el-table-column>
              <el-table-column label="定标(万)" width="120" align="right"><template #default="{ row }"><span class="num">{{ wan(row.awardAmount) }}</span></template></el-table-column>
              <el-table-column label="偏差(万)" width="120" align="right"><template #default="{ row }"><span class="num" :class="devClass(row.deviation)">{{ wan(row.deviation) }}</span></template></el-table-column>
              <el-table-column label="偏差率" width="100" align="center"><template #default="{ row }">{{ pct(row.deviationPct) }}</template></el-table-column>
              <el-table-column label="周期(天)" width="100" align="center"><template #default="{ row }">{{ row.cycleDays == null ? '-' : row.cycleDays }}</template></el-table-column>
            </el-table>
          </el-card>
        </template>

        <!-- 费用 -->
        <template v-if="tab === 'expense' && data">
          <el-row :gutter="12" class="stat-row">
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">三费总额(万)</div><div class="value">{{ wan(data.threeFee?.total) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">销售费占比</div><div class="value">{{ pct(data.threeFee?.salesRatio) }}</div></el-card></el-col>
            <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">预算执行（部门数）</div><div class="value">{{ (data.budgetExec || []).length }}</div></el-card></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="14"><el-card shadow="never"><template #header><span class="card-title">预算执行（部门视角四栏）</span></template><ChartBox :option="budgetOption" height="320px" /></el-card></el-col>
            <el-col :span="10"><el-card shadow="never"><template #header><span class="card-title">三费占比</span></template><ChartBox :option="threeFeeOption" height="320px" /></el-card></el-col>
          </el-row>
        </template>

        <el-empty v-if="loaded && !data" description="暂无数据" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { cockpitTopic } from '../../../api/cockpit'
import { wan, money, fmtDate } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const TOPICS = [
  { value: 'value', label: '货值分析' },
  { value: 'cost', label: '成本分析' },
  { value: 'sales', label: '销售分析' },
  { value: 'proc', label: '招采分析' },
  { value: 'expense', label: '费用分析' }
]

const tab = ref('value')
const loading = ref(false)
const loaded = ref(false)
const data = ref(null)
const cache = ref({})

const pct = v => (v == null || v === '' ? '-' : Number(v).toFixed(1) + '%')
const devClass = v => (v == null ? '' : Number(v) > 0 ? 'dev-up' : Number(v) < 0 ? 'dev-down' : '')
const sumOf = (arr, key) => (arr || []).reduce((s, r) => s + Number(r[key] || 0), 0)

const switchTab = async t => {
  tab.value = t
  if (cache.value[t]) { data.value = cache.value[t]; return }
  loading.value = true
  try {
    const res = await cockpitTopic(t)
    const d = res?.data || null
    cache.value[t] = d
    data.value = d
    loaded.value = true
  } finally {
    loading.value = false
  }
}

// ===== 货值 =====
const valueDistOption = computed(() => ({
  color: ['#185fa5', '#5687ba', '#67c23a', '#e6a23c', '#f56c6c', '#8aadd2'],
  tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${wan(p.value)} 万元（${p.percent}%）` },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 8 },
  series: [{
    name: '货值', type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: (data.value?.distribution || []).map(d => ({ name: d.productCategory || '其他', value: Number(d.totalValue || 0) }))
  }]
}))
const saleTrendOption = computed(() => {
  const rows = data.value?.saleTrend || []
  return {
    color: ['#185fa5'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    grid: { left: 70, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(r => r.month), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{ name: '签约额', type: 'line', smooth: true, areaStyle: { opacity: 0.12 }, data: rows.map(r => Number(r.amount || 0)) }]
  }
})

// ===== 成本 =====
const accountOption = computed(() => {
  const rows = (data.value?.accountUnit || []).slice(0, 12)
  const names = rows.map(r => r.accountName || '-')
  return {
    color: ['#185fa5', '#e6a23c'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 10 },
    grid: { left: 70, right: 70, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 6 ? 25 : 0 } },
    yAxis: [
      { type: 'value', name: '金额(万)', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
      { type: 'value', name: '单方(元)', splitLine: { show: false } }
    ],
    series: [
      { name: '金额', type: 'bar', barWidth: 22, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.amount || 0)) },
      { name: '单方', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 7, data: rows.map(r => Number(r.unitCost || 0)) }
    ]
  }
})
const changeOption = computed(() => {
  const rows = (data.value?.changeRateRanking || []).slice(0, 10)
  const names = rows.map(r => r.projectName || '-')
  return {
    color: ['#f56c6c'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toFixed(2) + '%' },
    grid: { left: 60, right: 30, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 5 ? 25 : 0 } },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [{
      name: '变更率', type: 'bar', barWidth: 24, itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: rows.map(r => { const c = Number(r.contracted || 0); return c > 0 ? Number((Number(r.confirmedChange || 0) / c * 100).toFixed(2)) : 0 })
    }]
  }
})

// ===== 销售 =====
const priceOption = computed(() => {
  const p = data.value?.priceAchievement || {}
  return {
    color: ['#185fa5', '#8aadd2'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toLocaleString('zh-CN') + ' 元' },
    grid: { left: 80, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['成交均价', '表价均价'], axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{ name: '均价', type: 'bar', barWidth: 60, itemStyle: { borderRadius: [6, 6, 0, 0] }, data: [Number(p.avgContractPrice || 0), Number(p.avgTablePrice || 0)], label: { show: true, position: 'top', formatter: x => Number(x.value).toLocaleString('zh-CN') } }]
  }
})
const channelOption = computed(() => ({
  color: ['#185fa5', '#5687ba', '#67c23a', '#e6a23c', '#f56c6c', '#8aadd2'],
  tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${wan(p.value)} 万元（${p.percent}%）` },
  legend: { bottom: 0, type: 'scroll', itemWidth: 12, itemHeight: 8 },
  series: [{
    name: '渠道', type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'],
    avoidLabelOverlap: true,
    itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: (data.value?.channelEffect || []).map(c => ({ name: c.supplier || '未知', value: Number(c.amount || 0) }))
  }]
}))

// ===== 招采 =====
const deviationOption = computed(() => {
  const rows = (data.value?.deviation || []).slice(0, 12)
  const names = rows.map(r => r.title || '-')
  return {
    color: ['#8aadd2', '#185fa5'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 10 },
    grid: { left: 70, right: 20, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 10, interval: 0, rotate: names.length > 5 ? 30 : 0 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '立项预算', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.budgetAmount || 0)) },
      { name: '定标金额', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.awardAmount || 0)) }
    ]
  }
})
const collusionOption = computed(() => {
  const rows = data.value?.collusionStats || []
  const names = rows.map(r => `${r.ruleCode || ''}·${r.riskLevel || ''}`)
  return {
    color: ['#f56c6c'],
    tooltip: { trigger: 'axis', valueFormatter: v => v + ' 次' },
    grid: { left: 50, right: 20, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 5 ? 25 : 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '次数', type: 'bar', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.cnt || 0)) }]
  }
})

// ===== 费用 =====
const budgetOption = computed(() => {
  const rows = (data.value?.budgetExec || []).slice(0, 12)
  const names = rows.map(r => r.deptName || '-')
  return {
    color: ['#185fa5', '#5687ba', '#67c23a', '#e6a23c'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 10 },
    grid: { left: 70, right: 20, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 6 ? 25 : 0 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '预算', type: 'bar', data: rows.map(r => Number(r.budget || 0)) },
      { name: '占用', type: 'bar', data: rows.map(r => Number(r.occupy || 0)) },
      { name: '核销', type: 'bar', data: rows.map(r => Number(r.writeoff || 0)) }
    ]
  }
})
const threeFeeOption = computed(() => {
  const f = data.value?.threeFee || {}
  return {
    color: ['#185fa5', '#5687ba', '#e6a23c'],
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${wan(p.value)} 万元（${p.percent}%）` },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 8 },
    series: [{
      name: '三费', type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { name: '销售费用', value: Number(f.salesFee || 0) },
        { name: '管理费用', value: Number(f.adminFee || 0) },
        { name: '财务费用', value: Number(f.finFee || 0) }
      ]
    }]
  }
})

onMounted(() => switchTab('value'))
</script>

<style scoped>
.cockpit-topic { max-width: 1560px; }
.topic-layout { display: flex; gap: 12px; align-items: flex-start; }
.topic-nav { flex: 0 0 150px; position: sticky; top: 8px; background: #fff; border: 1px solid #eef0f3; border-radius: 10px; padding: 8px; display: flex; flex-direction: column; gap: 4px; }
.topic-nav-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; font-size: 14px; color: #4b5563; display: flex; align-items: center; gap: 8px; transition: background .15s; }
.topic-nav-item:hover { background: #f5f8fc; }
.topic-nav-item.active { background: #185fa5; color: #fff; font-weight: 600; }
.tn-dot { width: 8px; height: 8px; border-radius: 50%; background: #c0c4cc; }
.topic-nav-item.active .tn-dot { background: #fff; }
.topic-body { flex: 1; min-width: 0; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; font-weight: 700; color: #185fa5; }
.stat-card .value-blue { color: #185fa5; }
.stat-card .label { font-size: 12.5px; color: #6b7280; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.list-card { margin-top: 12px; }
.num { font-variant-numeric: tabular-nums; }
.dev-up { color: #f56c6c; font-weight: 600; }
.dev-down { color: #67c23a; font-weight: 600; }
</style>
