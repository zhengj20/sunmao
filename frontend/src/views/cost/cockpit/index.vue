<template>
  <div class="page-container cost-cockpit">
    <!-- 顶部 KPI -->
    <div class="hero" v-loading="loading">
      <div class="hero-head">
        <div>
          <div class="hero-title">成本驾驶舱</div>
          <div class="hero-sub">{{ today }} · 目标 / 动态 / 实际三算 · 成本构成与预警</div>
        </div>
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>
      <div class="hero-cards">
        <div class="hero-card">
          <div class="hc-label">目标成本</div>
          <div class="hc-value num">{{ cny(kpi.target) }}</div>
          <div class="hc-foot">最新 APPROVED 版本</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">动态成本</div>
          <div class="hc-value num" :class="kpi.dynamic > kpi.target ? 'dev-up' : ''">{{ cny(kpi.dynamic) }}</div>
          <div class="hc-foot">已签+确认+预估+非合约</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">实际发生成本</div>
          <div class="hc-value num">{{ cny(kpi.actual) }}</div>
          <div class="hc-foot">已签+确认变更+无效</div>
        </div>
        <div class="hero-card" :class="kpi.remaining < 0 ? 'warn' : ''">
          <div class="hc-label">成本余量</div>
          <div class="hc-value num">{{ cny(kpi.remaining) }}</div>
          <div class="hc-foot">目标 − 动态（可负）</div>
        </div>
        <div class="hero-card" :class="kpi.devRate > 0 ? 'warn' : ''">
          <div class="hc-label">偏差率（动态 vs 目标）</div>
          <div class="hc-value num" :class="kpi.devRate > 0 ? 'dev-up' : 'dev-down'">{{ kpi.devRate == null ? '--' : sign(kpi.devRate) + '%' }}</div>
          <div class="hc-foot">超目标为正</div>
        </div>
        <div class="hero-card" :class="kpi.changeRate >= 5 ? 'warn' : ''">
          <div class="hc-label">变更率</div>
          <div class="hc-value num" :class="kpi.changeRate >= 5 ? 'dev-up' : ''">{{ kpi.changeRate }}%</div>
          <div class="hc-foot">确认变更 / 已签</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">无效成本</div>
          <div class="hc-value num">{{ cny(kpi.invalid) }}</div>
          <div class="hc-foot">非合约已确认支出</div>
        </div>
        <div class="hero-card" :class="warningCount ? 'warn' : ''">
          <div class="hc-label">成本预警</div>
          <div class="hc-value num">{{ warningCount }}</div>
          <div class="hc-foot">超目标 / 余量 / 变更率</div>
        </div>
      </div>
    </div>

    <!-- 图表行 1：构成 / 趋势 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">动态成本构成</span></template>
          <ChartBox v-if="composition.length" :option="compositionOption" height="280px" />
          <el-empty v-else description="暂无快照数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><span class="card-title">动态成本月度趋势（虚线为目标成本）</span></template>
          <ChartBox v-if="history.length" :option="trendOption" height="280px" />
          <el-empty v-else description="暂无历史快照" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 2：三算对比 / 科目排名 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">三算对比（目标 / 动态 / 实际）</span></template>
          <ChartBox v-if="threeWay" :option="threeWayOption" height="300px" />
          <el-empty v-else description="暂无数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span class="card-title">科目成本 Top（已签 + 确认变更）</span></template>
          <el-table :data="accountTop" size="small" stripe empty-text="暂无科目数据" max-height="300">
            <el-table-column label="#" width="50" align="center">
              <template #default="{ $index }"><span class="rank" :class="$index < 3 ? 'top' : ''">{{ $index + 1 }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="科目" min-width="160" show-overflow-tooltip />
            <el-table-column label="已签合同" width="120" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.contracted) }}</span></template>
            </el-table-column>
            <el-table-column label="确认变更" width="120" align="right">
              <template #default="{ row }"><span class="num" :style="row.change > 0 ? 'color:#c45656' : ''">{{ money(row.change) }}</span></template>
            </el-table-column>
            <el-table-column label="合计" width="130" align="right">
              <template #default="{ row }"><span class="num" style="font-weight:700">{{ money(row.total) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警中心 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">成本预警中心（{{ warnings.length }}）</span>
          <div class="legend">
            <el-tag size="small" type="danger" effect="plain">高</el-tag>
            <el-tag size="small" type="warning" effect="plain">中</el-tag>
          </div>
        </div>
      </template>
      <div v-if="warnings.length" class="risk-list">
        <div v-for="(w, i) in warnings" :key="i" class="risk-item">
          <el-tag size="small" :type="w.level === 'HIGH' ? 'danger' : 'warning'" effect="light">{{ w.level === 'HIGH' ? '高' : '中' }}</el-tag>
          <span class="risk-src">{{ typeLabel(w.type) }}</span>
          <span class="risk-desc">{{ w.message }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无预警，成本状态良好" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { money } from '../../../utils/format'
import {
  fetchProjectOptions, fetchDynamicSnapshots, fetchThreeWayCompare, fetchCostWarnings
} from '../../../api/cost'
import ChartBox from '../../../components/ChartBox.vue'

const today = dayjs().format('YYYY年MM月DD日')
const loading = ref(false)
const projects = ref([])
const projectId = ref(null)
const latest = ref(null)
const history = ref([])
const threeWay = ref(null)
const warnings = ref([])
const warningCount = ref(0)

const typeLabel = t => ({ OVER_TARGET: '超目标', LOW_REMAINING: '余量不足', CHANGE_RATE_HIGH: '变更率高' }[t] || t)
const cny = v => {
  v = Number(v || 0)
  const a = Math.abs(v)
  if (a >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (a >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}
const sign = v => { v = Number(v || 0); return (v > 0 ? '+' : '') + v.toFixed(1) }

const kpi = computed(() => {
  const l = latest.value || {}
  const tw = threeWay.value || {}
  const target = Number(l.targetTotal) || 0
  const dynamic = Number(l.dynamicTotal) || 0
  const actual = Number(tw.actualTotal) || 0
  const remaining = Number(l.remaining) || 0
  const invalid = Number(l.nonContract) || 0
  const devRate = tw.targetVsDynamicRate != null ? Number(tw.targetVsDynamicRate) : (target > 0 ? (dynamic - target) / target * 100 : 0)
  const contracted = Number(l.contractedTotal) || 0
  const confirmed = Number(l.confirmedChange) || 0
  const changeRate = contracted > 0 ? Math.round(confirmed / contracted * 1000) / 10 : 0
  return { target, dynamic, actual, remaining, invalid, devRate, changeRate }
})

const composition = computed(() => {
  const l = latest.value || {}
  return [
    { name: '已签合同', value: Number(l.contractedTotal) || 0 },
    { name: '已确认变更', value: Number(l.confirmedChange) || 0 },
    { name: '预估变更', value: Number(l.estimatedChange) || 0 },
    { name: '非合约支出', value: Number(l.nonContract) || 0 }
  ].filter(d => d.value > 0)
})

const accountTop = computed(() => {
  if (!latest.value?.detailJson) return []
  try {
    const arr = JSON.parse(latest.value.detailJson) || []
    return arr
      .map(r => {
        const contracted = Number(r.contracted) || 0
        const change = Number(r.change) || 0
        return { name: r.accountName || '未分摊', contracted, change, total: contracted + change }
      })
      .sort((a, b) => b.total - a.total)
      .slice(0, 8)
  } catch {
    return []
  }
})

// 成本构成（环形）
const compositionOption = computed(() => ({
  color: ['#185fa5', '#f56c6c', '#e6a23c', '#909399'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} 元 ({d}%)' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  series: [{ type: 'pie', radius: ['46%', '72%'], center: ['50%', '44%'], label: { show: false }, data: composition.value }]
}))

// 月度趋势（动态成本折线 + 目标成本虚线）
const trendOption = computed(() => {
  const h = history.value
  return {
    color: ['#185fa5', '#e6a23c'],
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 60, right: 24, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: h.map(s => s.snapMonth), boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '动态成本', type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.08 }, data: h.map(s => Number(s.dynamicTotal) || 0) },
      { name: '目标成本', type: 'line', smooth: false, symbol: 'none', lineStyle: { type: 'dashed' }, data: h.map(s => Number(s.targetTotal) || 0) }
    ]
  }
})

// 三算对比（柱状）
const threeWayOption = computed(() => {
  const tw = threeWay.value
  if (!tw) return {}
  return {
    color: ['#8aadd2', '#185fa5', '#f56c6c'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
    grid: { left: 64, right: 24, top: 24, bottom: 30 },
    xAxis: { type: 'category', data: ['目标成本', '动态成本', '实际成本'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{
      type: 'bar', barWidth: 42, itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: [
        { value: Number(tw.targetTotal) || 0, itemStyle: { color: '#8aadd2' } },
        { value: Number(tw.dynamicTotal) || 0, itemStyle: { color: '#185fa5' } },
        { value: Number(tw.actualTotal) || 0, itemStyle: { color: '#f56c6c' } }
      ]
    }]
  }
})

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [snaps, tw, warn] = await Promise.all([
      fetchDynamicSnapshots({ projectId: projectId.value }).catch(() => []),
      fetchThreeWayCompare(projectId.value).catch(() => null),
      fetchCostWarnings(projectId.value).catch(() => null)
    ])
    const arr = (snaps || []).slice().sort((a, b) => (a.snapMonth < b.snapMonth ? -1 : 1))
    history.value = arr
    latest.value = arr.length ? arr[arr.length - 1] : null
    threeWay.value = tw
    warnings.value = warn?.warnings || []
    warningCount.value = warn?.warningCount || 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    await load()
  }
})
</script>

<style scoped>
.cost-cockpit { max-width: 1560px; }
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
.hc-value.dev-up { color: #ff9d9d; }
.hc-value.dev-down { color: #7ce38b; }
.hc-foot { font-size: 11.5px; opacity: .7; margin-top: 6px; }
.section-row { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: #eef2f7; color: #6b7280; font-weight: 700; font-size: 12px; }
.rank.top { background: #185fa5; color: #fff; }
.risk-list { display: flex; flex-direction: column; gap: 8px; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #eef0f3; border-radius: 8px; }
.risk-src { font-size: 12px; color: #185fa5; font-weight: 600; min-width: 64px; }
.risk-desc { flex: 1; font-size: 12.5px; color: #4b5563; }
.num { font-variant-numeric: tabular-nums; }
</style>
