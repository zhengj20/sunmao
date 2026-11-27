<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-tabs v-model="tab" class="inline-tabs">
          <el-tab-pane label="销售日报" name="daily" />
          <el-tab-pane label="去化率" name="turnover" />
          <el-tab-pane label="价格实现率" name="price" />
        </el-tabs>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 销售日报 -->
      <div v-show="tab === 'daily'">
        <el-row :gutter="12" class="stat-row">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">认购单数</div><div class="value">{{ daily.subscription?.count ?? 0 }}</div><div class="sub">定金 <span class="num">{{ wan(daily.subscription?.amount) }} 万</span></div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">签约单数</div><div class="value">{{ daily.contract?.count ?? 0 }}</div><div class="sub">金额 <span class="num">{{ wan(daily.contract?.amount) }} 万</span></div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">实收笔数</div><div class="value">{{ daily.receipt?.count ?? 0 }}</div><div class="sub">金额 <span class="num">{{ wan(daily.receipt?.amount) }} 万</span></div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">回款率</div><div class="value" style="color:#185fa5">{{ daily.collectionRate ?? 0 }}%</div></el-card>
          </el-col>
        </el-row>
        <el-card shadow="never">
          <template #header><span class="card-title">认购定金 vs 签约金额 vs 实收金额（万元）</span></template>
          <ChartBox :option="dailyBarOption" height="320px" />
        </el-card>
      </div>

      <!-- 去化率 -->
      <div v-show="tab === 'turnover'">
        <el-row :gutter="12" class="stat-row">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">总套数</div><div class="value">{{ turnover.totalRooms ?? 0 }}</div><div class="sub">已售 {{ turnover.soldRooms ?? 0 }} 套</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">总货值</div><div class="value">{{ wan(turnover.totalValue) }} 万</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">已售货值</div><div class="value" style="color:#67c23a">{{ wan(turnover.soldValue) }} 万</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card"><div class="label">去化率(货值)</div><div class="value" style="color:#185fa5">{{ turnover.turnoverRateValue ?? 0 }}%</div><div class="sub">套数口径 {{ turnover.turnoverRateCount ?? 0 }}%</div></el-card>
          </el-col>
        </el-row>
        <el-card shadow="never">
          <template #header><span class="card-title">货值去化构成（已售 / 未售）</span></template>
          <ChartBox :option="turnoverPieOption" height="320px" />
        </el-card>
      </div>

      <!-- 价格实现率 -->
      <div v-show="tab === 'price'">
        <el-row :gutter="12" class="stat-row">
          <el-col :span="8">
            <el-card shadow="never" class="stat-card"><div class="label">成交均价(元)</div><div class="value">{{ money(price.avgContractPrice) }}</div></el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-card"><div class="label">表价均价(元)</div><div class="value">{{ money(price.avgTablePrice) }}</div></el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-card"><div class="label">价格实现率</div><div class="value" style="color:#185fa5">{{ price.priceAchievementRate ?? 0 }}%</div></el-card>
          </el-col>
        </el-row>
        <el-card shadow="never">
          <template #header><span class="card-title">成交均价 vs 表价均价（元）</span></template>
          <ChartBox :option="priceBarOption" height="320px" />
        </el-card>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { money, wan } from '../../../utils/format'
import { fetchProjectOptions, reportDaily, reportTurnover, reportPriceAchievement } from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const BLUE = '#185fa5'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const tab = ref('daily')

const daily = ref({ subscription: { count: 0, amount: 0 }, contract: { count: 0, amount: 0 }, receipt: { count: 0, amount: 0 }, collectionRate: 0 })
const turnover = ref({})
const price = ref({})

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [d, t, p] = await Promise.all([
      reportDaily(projectId.value).catch(() => null),
      reportTurnover(projectId.value).catch(() => null),
      reportPriceAchievement(projectId.value).catch(() => null)
    ])
    daily.value = d || daily.value
    turnover.value = t || {}
    price.value = p || {}
  } finally {
    loading.value = false
  }
}

// 日报柱状图
const dailyBarOption = computed(() => {
  const d = daily.value || {}
  const names = ['认购定金', '签约金额', '实收金额']
  const values = [(d.subscription?.amount || 0), (d.contract?.amount || 0), (d.receipt?.amount || 0)].map(v => Number(v) / 10000)
  return {
    color: ['#5687ba', BLUE, '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toFixed(1) + ' 万元' },
    grid: { left: 60, right: 24, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 13 } },
    yAxis: { type: 'value', name: '万元' },
    series: [{ name: '金额', type: 'bar', barWidth: 56, data: values, itemStyle: { borderRadius: [6, 6, 0, 0] }, label: { show: true, position: 'top', formatter: p => Number(p.value).toFixed(1) } }]
  }
})

// 去化率环形图
const turnoverPieOption = computed(() => {
  const t = turnover.value || {}
  const sold = Number(t.soldValue || 0) / 10000
  const total = Number(t.totalValue || 0) / 10000
  const unsold = Math.max(total - sold, 0)
  return {
    color: [BLUE, '#c3d6e8'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} 万元 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      name: '货值', type: 'pie', radius: ['45%', '70%'], center: ['50%', '48%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { name: '已售货值', value: Number(sold.toFixed(2)) },
        { name: '未售货值', value: Number(unsold.toFixed(2)) }
      ]
    }]
  }
})

// 价格实现率柱状图
const priceBarOption = computed(() => {
  const p = price.value || {}
  const names = ['成交均价', '表价均价']
  const values = [Number(p.avgContractPrice || 0), Number(p.avgTablePrice || 0)]
  return {
    color: [BLUE, '#8aadd2'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toLocaleString('zh-CN') + ' 元' },
    grid: { left: 80, right: 24, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 13 } },
    yAxis: { type: 'value', name: '元' },
    series: [{ name: '均价', type: 'bar', barWidth: 56, data: values, itemStyle: { borderRadius: [6, 6, 0, 0] }, label: { show: true, position: 'top', formatter: p => Number(p.value).toLocaleString('zh-CN') } }]
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
.inline-tabs { flex: 1; }
.inline-tabs :deep(.el-tabs__item) { font-size: 14px; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #1f2937; }
.stat-card .sub { font-size: 12px; color: #909399; margin-top: 4px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
</style>
