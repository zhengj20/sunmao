<template>
  <div class="page-container exp-cockpit">
    <!-- 顶部 KPI -->
    <div class="hero" v-loading="loading">
      <div class="hero-head">
        <div>
          <div class="hero-title">费用驾驶舱</div>
          <div class="hero-sub">{{ today }} · 预算执行 / 三费占比 / 费效比 / 支出趋势与预警</div>
        </div>
        <el-button type="primary" plain @click="load">刷新</el-button>
      </div>
      <div class="hero-cards">
        <div class="hero-card" :class="kpi.execRate >= 100 ? 'warn' : ''">
          <div class="hc-label">预算执行率</div>
          <div class="hc-value num">{{ kpi.execRate == null ? '--' : kpi.execRate + '%' }}</div>
          <div class="hc-foot">已核销 / 总预算</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">最近月支出</div>
          <div class="hc-value num">{{ cny(kpi.lastMonth) }}</div>
          <div class="hc-foot">{{ kpi.lastMonthLabel || '--' }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">销售费用占比</div>
          <div class="hc-value num">{{ kpi.salesRatio }}%</div>
          <div class="hc-foot">三费构成</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">管理费用占比</div>
          <div class="hc-value num">{{ kpi.adminRatio }}%</div>
          <div class="hc-foot">三费构成</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">财务费用占比</div>
          <div class="hc-value num">{{ kpi.finRatio }}%</div>
          <div class="hc-foot">三费构成</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">费效比（营销费/签约额）</div>
          <div class="hc-value num">{{ kpi.effRatio }}%</div>
          <div class="hc-foot">签约额 {{ cny(kpi.signedTotal) }}</div>
        </div>
        <div class="hero-card" :class="kpi.surgeCount ? 'warn' : ''">
          <div class="hc-label">环比突增预警</div>
          <div class="hc-value num">{{ kpi.surgeCount }}</div>
          <div class="hc-foot">月环比增幅 &gt; 30%</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">高频报销人</div>
          <div class="hc-value" style="font-size:17px">{{ kpi.topReimburser || '--' }}</div>
          <div class="hc-foot">{{ kpi.topReimburserCnt ? kpi.topReimburserCnt + ' 单' : '' }}</div>
        </div>
      </div>
    </div>

    <!-- 图表行 1：趋势 / 三费 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span class="card-title">月度费用支出趋势（审批通过报销）</span></template>
          <ChartBox v-if="trend.length" :option="trendOption" height="280px" />
          <el-empty v-else description="暂无支出数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">三费占比（销售 / 管理 / 财务）</span></template>
          <ChartBox v-if="feeData.length" :option="feeOption" height="280px" />
          <el-empty v-else description="暂无费用数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 2：部门执行 / 科目执行 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">部门预算执行（预算 / 占用 / 核销）</span></template>
          <ChartBox v-if="byDept.length" :option="deptOption" height="300px" />
          <el-empty v-else description="暂无预算数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">科目预算执行（预算 / 占用 / 核销）</span></template>
          <ChartBox v-if="byAccount.length" :option="accountOption" height="300px" />
          <el-empty v-else description="暂无预算数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警中心 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">费用预警中心（{{ warnings.length }}）</span>
          <div class="legend">
            <el-tag size="small" type="danger" effect="plain">高</el-tag>
            <el-tag size="small" type="warning" effect="plain">中</el-tag>
          </div>
        </div>
      </template>
      <div v-if="warnings.length" class="risk-list">
        <div v-for="(w, i) in warnings" :key="i" class="risk-item">
          <el-tag size="small" :type="w.level === 'HIGH' ? 'danger' : 'warning'" effect="light">{{ w.level === 'HIGH' ? '高' : '中' }}</el-tag>
          <span class="risk-src">{{ w.src }}</span>
          <span class="risk-desc">{{ w.desc }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无预警，费用状态良好" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import {
  reportExec, reportThreeFee, reportAnomaly
} from '../../../api/expense'
import ChartBox from '../../../components/ChartBox.vue'

const today = dayjs().format('YYYY年MM月DD日')
const loading = ref(false)
const exec = ref(null)
const threeFee = ref(null)
const anomaly = ref(null)

const cny = v => {
  v = Number(v || 0)
  const a = Math.abs(v)
  if (a >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (a >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}

const trend = computed(() => anomaly.value?.monthlyTrend || [])
const byDept = computed(() => (exec.value?.byDept || []).slice(0, 8))
const byAccount = computed(() => (exec.value?.byAccount || []).slice(0, 8))

const kpi = computed(() => {
  const t = exec.value?.total || {}
  const budget = Number(t.budget) || 0
  const writeoff = Number(t.writeoff) || 0
  const fee = threeFee.value || {}
  const tr = trend.value
  const last = tr.length ? tr[tr.length - 1] : null
  const top = anomaly.value?.topReimbursers || []
  return {
    execRate: budget > 0 ? Math.round(writeoff / budget * 1000) / 10 : null,
    lastMonth: last ? Number(last.amt) || 0 : 0,
    lastMonthLabel: last ? last.month : '',
    salesRatio: Number(fee.salesRatio) || 0,
    adminRatio: Number(fee.adminRatio) || 0,
    finRatio: Number(fee.finRatio) || 0,
    effRatio: Number(fee.costEfficiencyRatio) || 0,
    signedTotal: Number(fee.signedTotal) || 0,
    surgeCount: (anomaly.value?.surges || []).length,
    topReimburser: top.length ? top[0].userName : '',
    topReimburserCnt: top.length ? top[0].cnt : 0
  }
})

const feeData = computed(() => {
  const fee = threeFee.value || {}
  return [
    { name: '销售费用', value: Number(fee.salesFee) || 0 },
    { name: '管理费用', value: Number(fee.adminFee) || 0 },
    { name: '财务费用', value: Number(fee.finFee) || 0 }
  ].filter(d => d.value > 0)
})

// 预警：环比突增(高) + 预算超核/余额不足(高) + 执行率≥100%(中)
const warnings = computed(() => {
  const list = []
  for (const s of anomaly.value?.surges || []) {
    list.push({ level: 'HIGH', src: '环比突增', desc: `${s.month} 支出 ${cny(s.amount)}，环比 +${s.growthPct}%（上月 ${cny(s.prevAmount)}）` })
  }
  for (const d of exec.value?.byDept || []) {
    const budget = Number(d.budget) || 0
    const writeoff = Number(d.writeoff) || 0
    if (budget > 0 && writeoff > budget) {
      list.push({ level: 'HIGH', src: '预算超支', desc: `${d.deptName} 已核销 ${cny(writeoff)}，超出预算 ${cny(writeoff - budget)}` })
    }
  }
  const total = exec.value?.total || {}
  const budget = Number(total.budget) || 0
  const writeoff = Number(total.writeoff) || 0
  if (budget > 0 && writeoff > 0 && writeoff / budget >= 1) {
    list.push({ level: 'MID', src: '预算执行', desc: `整体预算执行率已达 ${kpi.value.execRate}%，请关注后续预算余量` })
  }
  return list
})

const trendOption = computed(() => ({
  color: ['#185fa5', '#e6a23c'],
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
  grid: { left: 60, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: trend.value.map(t => t.month), boundaryGap: false },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    { name: '支出金额', type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.08 }, data: trend.value.map(t => Number(t.amt) || 0) },
    { name: '报销单数', type: 'bar', barWidth: 16, itemStyle: { opacity: 0.5, borderRadius: [3, 3, 0, 0] }, data: trend.value.map(t => Number(t.cnt) || 0) }
  ]
}))

const feeOption = computed(() => ({
  color: ['#185fa5', '#e6a23c', '#67c23a'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} 元 ({d}%)' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  series: [{ type: 'pie', radius: ['46%', '72%'], center: ['50%', '44%'], label: { show: false }, data: feeData.value }]
}))

const execOption = (rows, nameKey) => ({
  color: ['#8aadd2', '#e6a23c', '#185fa5'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 64, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: rows.map(r => r[nameKey]), axisLabel: { fontSize: 11, interval: 0, rotate: rows.length > 5 ? 20 : 0 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    { name: '预算', type: 'bar', barWidth: 12, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: rows.map(r => Number(r.budget) || 0) },
    { name: '占用', type: 'bar', barWidth: 12, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: rows.map(r => Number(r.occupy) || 0) },
    { name: '核销', type: 'bar', barWidth: 12, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: rows.map(r => Number(r.writeoff) || 0) }
  ]
})
const deptOption = computed(() => execOption(byDept.value, 'deptName'))
const accountOption = computed(() => execOption(byAccount.value, 'accountName'))

const load = async () => {
  loading.value = true
  try {
    const [e, f, a] = await Promise.all([
      reportExec().catch(() => null),
      reportThreeFee().catch(() => null),
      reportAnomaly().catch(() => null)
    ])
    exec.value = e
    threeFee.value = f
    anomaly.value = a
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.exp-cockpit { max-width: 1560px; }
.hero { background: linear-gradient(120deg, #0c2d4e 0%, #185fa5 78%, #2a74bd 100%); border-radius: 10px; padding: 18px 20px 20px; margin-bottom: 12px; color: #fff; }
.hero-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; gap: 12px; flex-wrap: wrap; }
.hero-title { font-size: 19px; font-weight: 700; letter-spacing: 2px; }
.hero-sub { font-size: 12px; opacity: .78; margin-top: 4px; }
.hero-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.hero-card { background: rgba(255,255,255,.10); border: 1px solid rgba(255,255,255,.16); border-radius: 8px; padding: 14px 16px; transition: background .15s; }
.hero-card:hover { background: rgba(255,255,255,.18); }
.hero-card.warn { background: rgba(245,108,108,.16); border-color: rgba(255,180,180,.35); }
.hc-label { font-size: 12px; opacity: .82; margin-bottom: 8px; }
.hc-value { font-size: 22px; font-weight: 700; color: #fff; }
.hc-foot { font-size: 11.5px; opacity: .7; margin-top: 6px; }
.section-row { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.risk-list { display: flex; flex-direction: column; gap: 8px; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #eef0f3; border-radius: 8px; }
.risk-src { font-size: 12px; color: #185fa5; font-weight: 600; min-width: 64px; }
.risk-desc { flex: 1; font-size: 12.5px; color: #4b5563; }
.num { font-variant-numeric: tabular-nums; }
</style>
