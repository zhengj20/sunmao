<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="toolbar-title">费用分析（全集团口径）</span>
        <div class="actions">
          <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!loaded" description="加载中 / 暂无数据" :image-size="80" />

    <template v-else>
      <el-tabs v-model="tab">
        <!-- 预算执行 -->
        <el-tab-pane label="预算执行" name="exec">
          <el-row :gutter="12" class="stat-row">
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">预算总额（万元）</div><div class="value">{{ wan(exec.total?.budget) }}</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已占用（万元）</div><div class="value">{{ wan(exec.total?.occupy) }}</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已核销（万元）</div><div class="value">{{ wan(exec.total?.writeoff) }}</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">余额（万元）</div><div class="value">{{ wan(exec.total?.balance) }}</div></el-card></el-col>
          </el-row>
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="card-title">预算执行（四栏：预算 / 占用 / 核销 / 余额）</span>
                <el-radio-group v-model="execView" size="small">
                  <el-radio-button value="byDept">部门</el-radio-button>
                  <el-radio-button value="byAccount">科目</el-radio-button>
                  <el-radio-button value="byOrg">组织</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <ChartBox :option="execBarOption" height="340px" />
          </el-card>
          <el-card shadow="never" class="list-card">
            <template #header><span class="card-title">{{ execViewName }}视角明细</span></template>
            <el-table :data="execRows" border stripe size="small">
              <el-table-column prop="name" :label="execViewName" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.name }}</template>
              </el-table-column>
              <el-table-column label="预算（元）" width="140" align="right"><template #default="{ row }"><span class="num">{{ money(row.budget) }}</span></template></el-table-column>
              <el-table-column label="占用（元）" width="130" align="right"><template #default="{ row }"><span class="num">{{ money(row.occupy) }}</span></template></el-table-column>
              <el-table-column label="核销（元）" width="130" align="right"><template #default="{ row }"><span class="num">{{ money(row.writeoff) }}</span></template></el-table-column>
              <el-table-column label="余额（元）" width="140" align="right"><template #default="{ row }"><span class="num">{{ money(row.balance) }}</span></template></el-table-column>
              <el-table-column label="执行率" width="150" align="center">
                <template #default="{ row }">
                  <el-progress :percentage="execRate(row)" :stroke-width="12" :color="execRate(row) > 95 ? '#c45656' : '#185fa5'" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <!-- 三费占比 -->
        <el-tab-pane label="三费占比" name="fee">
          <el-row :gutter="12" class="stat-row">
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">三费总额（万元）</div><div class="value">{{ wan(fee.total) }}</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">签约总额（万元）</div><div class="value">{{ wan(fee.signedTotal) }}</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">费效比（营销费/签约额）</div><div class="value">{{ fee.costEfficiencyRatio ?? 0 }}%</div></el-card></el-col>
            <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">销售费用占比</div><div class="value">{{ fee.salesRatio ?? 0 }}%</div></el-card></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-card shadow="never"><template #header><span class="card-title">三费构成（管理 / 销售 / 财务）</span></template>
                <ChartBox :option="feePieOption" height="340px" />
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never"><template #header><span class="card-title">三费明细</span></template>
                <el-table :data="feeRows" border stripe size="small">
                  <el-table-column prop="name" label="费用类别" width="120" align="center" />
                  <el-table-column label="金额（万元）" align="right"><template #default="{ row }"><span class="num">{{ wan(row.value) }}</span></template></el-table-column>
                  <el-table-column label="占比" width="140" align="center">
                    <template #default="{ row }"><el-progress :percentage="Math.min(100, Number(row.ratio) || 0)" :stroke-width="12" :color="row.color" /></template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 异常识别 -->
        <el-tab-pane label="异常识别" name="anomaly">
          <el-card shadow="never">
            <template #header><span class="card-title">月度报销趋势（万元）</span></template>
            <ChartBox :option="trendOption" height="260px" />
          </el-card>
          <el-row :gutter="12" class="list-row">
            <el-col :span="12">
              <el-card shadow="never"><template #header><span class="card-title">环比突增（增幅 > 30%）</span></template>
                <el-table :data="anomaly.surges || []" border stripe size="small" empty-text="暂无突增">
                  <el-table-column prop="month" label="月份" width="100" align="center" />
                  <el-table-column prop="prevMonth" label="上月" width="100" align="center" />
                  <el-table-column label="本月（元）" align="right"><template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template></el-table-column>
                  <el-table-column label="上月（元）" align="right"><template #default="{ row }"><span class="num">{{ money(row.prevAmount) }}</span></template></el-table-column>
                  <el-table-column label="增幅" width="100" align="center">
                    <template #default="{ row }"><span style="color:#c45656;font-weight:700">{{ row.growthPct }}%</span></template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never"><template #header><span class="card-title">高频报销人（Top 5）</span></template>
                <el-table :data="anomaly.topReimbursers || []" border stripe size="small" empty-text="暂无数据">
                  <el-table-column prop="userName" label="报销人" width="120" align="center" />
                  <el-table-column prop="cnt" label="笔数" width="80" align="center" />
                  <el-table-column label="金额（元）" align="right"><template #default="{ row }"><span class="num">{{ money(row.amt) }}</span></template></el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { money, wan } from '../../../utils/format'
import { reportExec, reportThreeFee, reportAnomaly } from '../../../api/expense'
import ChartBox from '../../../components/ChartBox.vue'

const BLUE = '#185fa5'
const PALETTE = ['#185fa5', '#5687ba', '#c45656', '#67c23a', '#e6a23c']

const loading = ref(false)
const loaded = ref(false)
const tab = ref('exec')
const exec = ref({ byDept: [], byAccount: [], byOrg: [], total: {} })
const fee = ref({})
const anomaly = ref({ monthlyTrend: [], surges: [], topReimbursers: [] })

const load = async () => {
  loading.value = true
  try {
    const [e, f, a] = await Promise.all([
      reportExec().catch(() => null),
      reportThreeFee().catch(() => null),
      reportAnomaly().catch(() => null)
    ])
    if (e) exec.value = { byDept: e.byDept || [], byAccount: e.byAccount || [], byOrg: e.byOrg || [], total: e.total || {} }
    if (f) fee.value = f
    if (a) anomaly.value = { monthlyTrend: a.monthlyTrend || [], surges: a.surges || [], topReimbursers: a.topReimbursers || [] }
    loaded.value = true
  } finally {
    loading.value = false
  }
}

// ===== 预算执行 =====
const execView = ref('byDept')
const execViewName = computed(() => ({ byDept: '部门', byAccount: '科目', byOrg: '组织' }[execView.value]))
const execRows = computed(() => {
  const src = exec.value[execView.value] || []
  const nameKey = { byDept: 'deptName', byAccount: 'accountName', byOrg: 'orgName' }[execView.value]
  return src.map(r => ({ name: r[nameKey] || '-', budget: r.budget, occupy: r.occupy, writeoff: r.writeoff, balance: r.balance }))
})
const execRate = row => {
  const b = Number(row.budget) || 0
  if (!b) return 0
  return Math.min(100, Math.round(((Number(row.occupy) || 0) + (Number(row.writeoff) || 0)) / b * 100))
}
const execBarOption = computed(() => {
  const rows = execRows.value
  const cats = rows.map(r => r.name)
  const num = v => Number(v) / 10000
  return {
    color: ['#185fa5', '#5687ba', '#67c23a', '#e6a23c'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v).toFixed(2) + ' 万元' },
    legend: { bottom: 0 },
    grid: { left: 60, right: 24, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: cats, axisLabel: { fontSize: 12, interval: 0, rotate: cats.length > 6 ? 20 : 0 } },
    yAxis: { type: 'value', name: '万元' },
    series: [
      { name: '预算', type: 'bar', data: rows.map(r => Number(num(r.budget).toFixed(2))) },
      { name: '占用', type: 'bar', data: rows.map(r => Number(num(r.occupy).toFixed(2))) },
      { name: '核销', type: 'bar', data: rows.map(r => Number(num(r.writeoff).toFixed(2))) },
      { name: '余额', type: 'bar', data: rows.map(r => Number(num(r.balance).toFixed(2))) }
    ]
  }
})

// ===== 三费占比 =====
const feeRows = computed(() => [
  { name: '管理费用', value: Number(fee.value.adminFee) || 0, ratio: Number(fee.value.adminRatio) || 0, color: '#5687ba' },
  { name: '销售费用', value: Number(fee.value.salesFee) || 0, ratio: Number(fee.value.salesRatio) || 0, color: BLUE },
  { name: '财务费用', value: Number(fee.value.finFee) || 0, ratio: Number(fee.value.finRatio) || 0, color: '#e6a23c' }
])
const feePieOption = computed(() => ({
  color: ['#5687ba', BLUE, '#e6a23c'],
  tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${Number(p.value).toFixed(2)} 万元（${p.percent}%）` },
  legend: { bottom: 0 },
  series: [{
    name: '三费', type: 'pie', radius: ['45%', '70%'], center: ['50%', '46%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: feeRows.value.map(r => ({ name: r.name, value: Number((Number(r.value) / 10000).toFixed(2)) }))
  }]
}))

// ===== 异常识别 =====
const trendOption = computed(() => {
  const t = anomaly.value.monthlyTrend || []
  return {
    color: [BLUE],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toFixed(2) + ' 万元' },
    grid: { left: 60, right: 24, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: t.map(x => x.month), axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: '万元' },
    series: [{
      name: '报销额', type: 'line', smooth: true, areaStyle: { opacity: 0.12 },
      data: t.map(x => Number((Number(x.amt) / 10000).toFixed(2))),
      markPoint: { data: anomaly.value.surges.map(s => ({ name: s.month, coord: [s.month, Number((Number(s.amount) / 10000).toFixed(2))], value: '突增' })) }
    }]
  }
})

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar-title { font-weight: 700; color: #0c447c; font-size: 14px; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; font-weight: 700; color: #185fa5; }
.stat-card .label { font-size: 12.5px; color: #6b7280; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.list-card { margin-bottom: 12px; }
.list-row { margin-top: 0; }
</style>
