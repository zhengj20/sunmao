<template>
  <div class="page-container cockpit">
    <!-- 顶部核心指标（深色卡片） -->
    <div class="hero">
      <div class="hero-head">
        <div class="hero-title">集团经营驾驶舱</div>
        <div class="hero-sub">{{ today }} · 一期经营总览</div>
      </div>
      <div class="hero-cards">
        <div class="hero-card">
          <div class="hc-label">总货值（拿地基线，万元）</div>
          <div class="hc-value num">{{ wan(cards.totalValue) }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">签约额（万元）</div>
          <div class="hc-value num">{{ wan(cards.signedAmount) }}</div>
          <div class="hc-foot">去化率 {{ rate(cards.signedAmount, cards.supplyValue) }}%</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">累计回款（万元）</div>
          <div class="hc-value num">{{ wan(cards.receiptAmount) }}</div>
          <div class="hc-foot">回款率 {{ rate(cards.receiptAmount, cards.signedAmount) }}%</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">供货货值（万元）</div>
          <div class="hc-value num">{{ wan(cards.supplyValue) }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">动态成本（万元）</div>
          <div class="hc-value num">{{ wan(cards.dynamicCost) }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">动态利润（万元）</div>
          <div class="hc-value num" style="color:#7ce38b">{{ wan(cards.dynamicProfit) }}</div>
          <div class="hc-foot">利润率 {{ rate(cards.dynamicProfit, cards.dynamicValue) }}%</div>
        </div>
      </div>
    </div>

    <!-- 中部图表 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">货值去化</span></template>
          <ChartBox :option="sellOption" height="270px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">项目成本监控（目标 vs 动态）</span></template>
          <ChartBox :option="costOption" height="270px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">近6个月经营趋势（万元）</span></template>
          <ChartBox :option="trendOption" height="270px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 项目红黄绿灯 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">项目动态成本红黄绿灯</span>
          <div class="legend">
            <span class="dot green" /> ≤2% 正常
            <span class="dot yellow" /> ≤5% 关注
            <span class="dot red" /> &gt;5% 预警
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="projects" stripe>
        <el-table-column prop="name" label="项目" min-width="130" show-overflow-tooltip />
        <el-table-column label="项目状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dictLabel('project_status', row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标成本(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.targetTotal) }}</span></template>
        </el-table-column>
        <el-table-column label="动态成本(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.dynamicCost) }}</span></template>
        </el-table-column>
        <el-table-column label="成本偏差(元)" width="150" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ 'dev-up': row.costDeviation > 0, 'dev-down': row.costDeviation < 0 }">
              {{ row.costDeviation == null ? '-' : (row.costDeviation > 0 ? '+' : '') + money(row.costDeviation) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="动态利润(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.dynamicProfit) }}</span></template>
        </el-table-column>
        <el-table-column label="健康灯" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.light" :content="lightText(row)" placement="top">
              <span class="light" :class="row.light.toLowerCase()" />
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { cockpitOverview } from '../../../api/cockpit'
import { dictLabel, money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const today = dayjs().format('YYYY年MM月DD日')
const loading = ref(false)
const cards = ref({})
const projects = ref([])
const trend = ref([])

const rate = (a, b) => {
  const av = Number(a || 0), bv = Number(b || 0)
  return bv > 0 ? ((av / bv) * 100).toFixed(1) : '0.0'
}

const lightText = row => {
  const map = {
    GREEN: '动态成本与目标偏差≤2%，运行正常',
    YELLOW: '动态成本偏差≤5%，需关注',
    RED: '动态成本偏差>5%，成本预警'
  }
  return map[row.light] || ''
}

const load = async () => {
  loading.value = true
  try {
    const data = await cockpitOverview()
    cards.value = { ...data.cards, dynamicValue: Number(data.cards?.supplyValue || 0) }
    projects.value = data.projects || []
    trend.value = data.trend || []
  } finally {
    loading.value = false
  }
}

// 货值去化：供货/签约/回款
const sellOption = computed(() => ({
  color: ['#185fa5', '#2a74bd', '#67c23a'],
  tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
  grid: { left: 80, right: 20, top: 36, bottom: 30 },
  xAxis: { type: 'category', data: ['供货货值', '签约额', '累计回款'], axisLabel: { fontSize: 12 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    {
      name: '金额',
      type: 'bar',
      barWidth: 40,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: [
        Number(cards.value.supplyValue || 0),
        Number(cards.value.signedAmount || 0),
        Number(cards.value.receiptAmount || 0)
      ],
      label: { show: true, position: 'top', formatter: p => wan(p.value) }
    }
  ]
}))

// 成本监控：目标 vs 动态
const costOption = computed(() => {
  const names = projects.value.map(p => p.name)
  return {
    color: ['#8aadd2', '#185fa5'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 80, right: 20, top: 36, bottom: 44 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '目标成本', type: 'bar', barWidth: 22, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: projects.value.map(p => Number(p.targetTotal || 0)) },
      { name: '动态成本', type: 'bar', barWidth: 22, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: projects.value.map(p => Number(p.dynamicCost || 0)) }
    ]
  }
})

// 近6月趋势
const trendOption = computed(() => {
  const months = trend.value.map(t => t.month)
  return {
    color: ['#185fa5', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 16, itemHeight: 8 },
    grid: { left: 80, right: 20, top: 36, bottom: 44 },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      {
        name: '动态成本', type: 'line', smooth: true, symbolSize: 7,
        areaStyle: { color: 'rgba(24,95,165,.12)' },
        data: trend.value.map(t => Number(t.dynamicCost || 0))
      },
      {
        name: '动态利润', type: 'line', smooth: true, symbolSize: 7,
        areaStyle: { color: 'rgba(103,194,58,.12)' },
        data: trend.value.map(t => Number(t.dynamicProfit || 0))
      }
    ]
  }
})

onMounted(load)
</script>

<style scoped>
.cockpit { max-width: 1560px; }
.hero {
  background: linear-gradient(120deg, #0c2d4e 0%, #185fa5 78%, #2a74bd 100%);
  border-radius: 10px; padding: 18px 20px 20px; margin-bottom: 12px; color: #fff;
}
.hero-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 14px; }
.hero-title { font-size: 19px; font-weight: 700; letter-spacing: 2px; }
.hero-sub { font-size: 12px; opacity: .75; }
.hero-cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.hero-card {
  background: rgba(255,255,255,.10); border: 1px solid rgba(255,255,255,.16);
  border-radius: 8px; padding: 14px 16px;
}
.hc-label { font-size: 12px; opacity: .8; margin-bottom: 8px; white-space: nowrap; }
.hc-value { font-size: 22px; font-weight: 700; color: #fff; }
.hc-foot { font-size: 11.5px; opacity: .7; margin-top: 6px; }
.chart-row { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot.green { background: #67c23a; }
.dot.yellow { background: #e6a23c; }
.dot.red { background: #f56c6c; }
.light { width: 14px; height: 14px; border-radius: 50%; display: inline-block; }
.light.green { background: #67c23a; box-shadow: 0 0 6px rgba(103,194,58,.6); }
.light.yellow { background: #e6a23c; box-shadow: 0 0 6px rgba(230,162,60,.6); }
.light.red { background: #f56c6c; box-shadow: 0 0 6px rgba(245,108,108,.7); }
.dev-up { color: #f56c6c; font-weight: 600; }
.dev-down { color: #67c23a; font-weight: 600; }
</style>
