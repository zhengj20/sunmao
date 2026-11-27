<template>
  <div class="page-container inv-cockpit">
    <!-- 顶部 KPI -->
    <div class="hero" v-loading="loading">
      <div class="hero-head">
        <div>
          <div class="hero-title">投资驾驶舱</div>
          <div class="hero-sub">{{ today }} · 基线测算 / 动态利润 / 供销存 / 预警</div>
        </div>
      </div>
      <div class="hero-cards">
        <div class="hero-card">
          <div class="hc-label">投资总额</div>
          <div class="hc-value num">{{ cny(cards.totalInvestment) }}</div>
          <div class="hc-foot">基线总成本</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">货值总额</div>
          <div class="hc-value num">{{ cny(cards.totalSaleValue) }}</div>
          <div class="hc-foot">基线总货值</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">预期净利润</div>
          <div class="hc-value num">{{ cny(cards.totalNetProfit) }}</div>
          <div class="hc-foot">拿地测算基线</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">最新动态利润</div>
          <div class="hc-value num">{{ cny(cards.totalDynamicProfit) }}</div>
          <div class="hc-foot">月度快照最新值</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">加权净利率</div>
          <div class="hc-value num">{{ cards.avgProfitRate != null ? cards.avgProfitRate + '%' : '--' }}</div>
          <div class="hc-foot">Σ净利 ÷ Σ货值</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">平均 IRR</div>
          <div class="hc-value num">{{ cards.avgIrr != null ? cards.avgIrr + '%' : '--' }}</div>
          <div class="hc-foot">基线版本均值</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">平均回收期</div>
          <div class="hc-value num">{{ cards.avgPayback != null ? cards.avgPayback + ' 年' : '--' }}</div>
          <div class="hc-foot">基线版本均值</div>
        </div>
        <div class="hero-card" :class="shortfall ? 'warn' : ''">
          <div class="hc-label">利润缺口项目</div>
          <div class="hc-value num">{{ shortfall }}</div>
          <div class="hc-foot">动态 &lt; 拿地基线</div>
        </div>
      </div>
    </div>

    <!-- 图表行 1：利润对比 / 利润趋势 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">项目利润对比（基线 vs 动态）</span></template>
          <ChartBox v-if="profitRank.length" :option="profitRankOption" height="300px" />
          <el-empty v-else description="暂无利润数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">动态利润月度趋势（跨项目合计）</span></template>
          <ChartBox v-if="profitTrend.length" :option="trendOption" height="300px" />
          <el-empty v-else description="暂无月度快照" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 2：业态构成 / 供销存 / IRR 排名 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">货值业态构成</span></template>
          <ChartBox v-if="categoryValue.length" :option="categoryOption" height="280px" />
          <el-empty v-else description="暂无货值台账" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">货值供销存月度趋势</span></template>
          <ChartBox v-if="flowTrend.length" :option="flowOption" height="280px" />
          <el-empty v-else description="暂无供销存数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">IRR / 净利率排名</span></template>
          <ChartBox v-if="irrRank.length" :option="irrOption" height="280px" />
          <el-empty v-else description="暂无基线数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警中心 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">投资预警中心（{{ alerts.length }}）</span>
          <div class="legend">
            <el-tag size="small" type="danger" effect="plain">高</el-tag>
            <el-tag size="small" type="warning" effect="plain">中</el-tag>
          </div>
        </div>
      </template>
      <div v-if="alerts.length" class="risk-list">
        <div v-for="(a, i) in alerts" :key="i" class="risk-item" @click="go(a.link)">
          <el-tag size="small" :type="a.level === 'HIGH' ? 'danger' : 'warning'" effect="light">
            {{ a.level === 'HIGH' ? '高' : '中' }}
          </el-tag>
          <span class="risk-src">{{ a.title }}</span>
          <span class="risk-desc">{{ a.desc }}</span>
          <span class="risk-link">处理 →</span>
        </div>
      </div>
      <el-empty v-else description="暂无预警，投资状态良好" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { investCockpit } from '../../../api/invest'
import ChartBox from '../../../components/ChartBox.vue'

const router = useRouter()
const go = path => router.push(path)

const today = dayjs().format('YYYY年MM月DD日')
const loading = ref(false)
const data = ref(null)

const cards = computed(() => data.value?.cards || {})
const profitRank = computed(() => data.value?.profitRank || [])
const irrRank = computed(() => data.value?.irrRank || [])
const profitTrend = computed(() => data.value?.profitTrend || [])
const categoryValue = computed(() => data.value?.categoryValue || [])
const flowTrend = computed(() => data.value?.flowTrend || [])
const alerts = computed(() => data.value?.alerts || [])
const shortfall = computed(() => Number(cards.value.shortfallCount) || 0)

const cny = v => {
  v = Number(v || 0)
  const a = Math.abs(v)
  if (a >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (a >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}

// 利润对比（分组柱状）
const profitRankOption = computed(() => ({
  color: ['#8aadd2', '#185fa5'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 64, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: profitRank.value.map(d => d.projectName), axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 1e8).toFixed(0) + '亿' } },
  series: [
    { name: '基线净利', type: 'bar', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: profitRank.value.map(d => Number(d.baseline) || 0) },
    { name: '动态利润', type: 'bar', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: profitRank.value.map(d => Number(d.dynamic) || 0) }
  ]
}))

// 利润趋势（动态利润 + 动态成本）
const trendOption = computed(() => ({
  color: ['#185fa5', '#e6a23c'],
  tooltip: { trigger: 'axis', valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
  grid: { left: 64, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: profitTrend.value.map(t => t.month), boundaryGap: false },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 1e8).toFixed(0) + '亿' } },
  series: [
    { name: '动态利润', type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.08 }, data: profitTrend.value.map(t => Number(t.dynamicProfit) || 0) },
    { name: '动态成本', type: 'line', smooth: true, symbol: 'none', lineStyle: { type: 'dashed' }, data: profitTrend.value.map(t => Number(t.dynamicCost) || 0) }
  ]
}))

// 业态构成（环形）
const categoryOption = computed(() => ({
  color: ['#185fa5', '#e6a23c', '#67c23a', '#909399', '#f56c6c'],
  tooltip: { trigger: 'item', formatter: p => `${p.name}: ${Number(p.value).toLocaleString()} 元 (${p.percent}%)` },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  series: [{
    type: 'pie', radius: ['46%', '72%'], center: ['50%', '44%'], label: { show: false },
    data: categoryValue.value.map(d => ({ name: d.category, value: Number(d.value) || 0 }))
  }]
}))

// 供销存趋势
const flowOption = computed(() => ({
  color: ['#185fa5', '#67c23a', '#e6a23c'],
  tooltip: { trigger: 'axis', valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 64, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: flowTrend.value.map(t => t.month), boundaryGap: false },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 1e8).toFixed(0) + '亿' } },
  series: [
    { name: '已售', type: 'line', smooth: true, symbolSize: 6, data: flowTrend.value.map(t => Number(t.sold) || 0) },
    { name: '新增供货', type: 'line', smooth: true, symbolSize: 6, data: flowTrend.value.map(t => Number(t.supply) || 0) },
    { name: '期末存货', type: 'line', smooth: true, symbol: 'none', areaStyle: { opacity: 0.08 }, data: flowTrend.value.map(t => Number(t.stock) || 0) }
  ]
}))

// IRR / 净利率排名
const irrOption = computed(() => ({
  color: ['#185fa5', '#67c23a'],
  tooltip: {
    trigger: 'axis', axisPointer: { type: 'shadow' },
    formatter: ps => {
      const row = irrRank.value[ps[0].dataIndex] || {}
      const lines = ps.map(p => `${p.marker}${p.seriesName}: ${p.value != null ? p.value + '%' : '-'}`)
      if (row.paybackYears != null) lines.push(`回收期: ${row.paybackYears} 年`)
      return lines.join('<br/>')
    }
  },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 48, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
  yAxis: { type: 'category', data: irrRank.value.map(d => d.projectName), axisLabel: { fontSize: 11 } },
  series: [
    { name: 'IRR', type: 'bar', barWidth: 14, itemStyle: { borderRadius: [0, 3, 3, 0] }, data: irrRank.value.map(d => d.irr != null ? Number(d.irr) : null) },
    { name: '净利率', type: 'bar', barWidth: 14, itemStyle: { borderRadius: [0, 3, 3, 0] }, data: irrRank.value.map(d => d.profitRate != null ? Number(d.profitRate) : null) }
  ]
}))

onMounted(async () => {
  loading.value = true
  try {
    data.value = await investCockpit()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.inv-cockpit { max-width: 1560px; }
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
.risk-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #eef0f3; border-radius: 8px; cursor: pointer; transition: background .15s; }
.risk-item:hover { background: #f5f9fd; }
.risk-src { font-size: 12px; color: #185fa5; font-weight: 600; min-width: 64px; }
.risk-desc { flex: 1; font-size: 12.5px; color: #4b5563; }
.risk-link { font-size: 12px; color: #9ca3af; }
.num { font-variant-numeric: tabular-nums; }
</style>
