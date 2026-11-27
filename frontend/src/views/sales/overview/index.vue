<template>
  <div class="page-container overview">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        <span class="hint">销售总览：项目整体销售进度 · 楼栋销控看板 · 户型去化 · 成交均价走势</span>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 项目整体进度 -->
      <el-card shadow="never" class="section-card">
        <div class="progress-wrap">
          <div class="progress-main">
            <div class="pm-label">整体去化率（货值口径）</div>
            <div class="pm-rate">{{ progress.turnoverRate ?? 0 }}<span>%</span></div>
          </div>
          <div class="progress-mid">
            <el-progress :percentage="Number(progress.turnoverRate) || 0" :stroke-width="18" :show-text="false" color="#185fa5" />
            <div class="pm-legend">
              <span>已售货值 <b class="c-blue">{{ wan(progress.soldValue) }}万</b></span>
              <span>未售货值 <b>{{ wan(progress.unsoldValue) }}万</b></span>
              <span>总货值 <b>{{ wan(progress.totalValue) }}万</b></span>
            </div>
          </div>
          <div class="progress-kpi">
            <div class="pk-item"><span>总套数</span><b>{{ progress.totalRooms ?? 0 }} 套</b></div>
            <div class="pk-item"><span>已售套数</span><b class="c-green">{{ progress.soldRooms ?? 0 }} 套</b></div>
            <div class="pk-item"><span>签约额</span><b class="c-blue">{{ wan(progress.saleAmount) }}万</b></div>
            <div class="pk-item"><span>回款额</span><b class="c-green">{{ wan(progress.receiptAmount) }}万</b></div>
            <div class="pk-item"><span>回款率</span><b class="c-blue">{{ progress.collectionRate ?? 0 }}%</b></div>
            <div class="pk-item"><span>成交均价</span><b>{{ money(progress.avgContractPrice) }} 元/㎡</b></div>
          </div>
        </div>
      </el-card>

      <!-- 楼栋销控看板 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span class="card-title">楼栋销控看板（{{ (buildings || []).length }} 栋）</span>
            <div class="legend">
              <span><i class="dot blue" />已售</span>
              <span><i class="dot gray" />未售</span>
            </div>
          </div>
        </template>
        <div v-if="buildings.length" class="bld-grid">
          <div v-for="b in buildings" :key="b.buildingCode" class="bld-card">
            <div class="bld-head">
              <span class="bld-code">{{ b.buildingCode || b.buildingName || '楼栋' }}</span>
              <el-tag size="small" :type="Number(b.countRate) >= 70 ? 'success' : Number(b.countRate) >= 40 ? 'warning' : 'info'">{{ b.countRate ?? 0 }}%</el-tag>
            </div>
            <el-progress :percentage="Number(b.countRate) || 0" :stroke-width="10" :show-text="false" color="#185fa5" />
            <div class="bld-meta">
              <span>已售 <b class="c-blue">{{ b.sold }}</b> / {{ b.total }} 套</span>
              <span>{{ money(b.totalArea) }} ㎡</span>
            </div>
            <div class="bld-value">
              <span>已售货值 <b class="c-blue">{{ wan(b.soldValue) }}万</b></span>
              <span>总货值 {{ wan(b.totalValue) }}万</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无楼栋销控数据" :image-size="80" />
      </el-card>

      <!-- 户型去化 + 均价走势 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">户型去化分布（套数）</span></template>
            <ChartBox v-if="(layoutBoard || []).length" :option="layoutOption" height="300px" />
            <el-empty v-else description="暂无房源" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">成交均价走势（元/㎡）</span></template>
            <ChartBox v-if="(priceTrend || []).length" :option="priceOption" height="300px" />
            <el-empty v-else description="暂无签约数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { wan, money } from '../../../utils/format'
import { fetchProjectOptions, dashboardOverview } from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const data = ref({})

const progress = computed(() => data.value.progress || {})
const buildings = computed(() => data.value.buildingBoard || [])
const layoutBoard = computed(() => data.value.layoutBoard || [])
const priceTrend = computed(() => data.value.priceTrend || [])

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    data.value = (await dashboardOverview(projectId.value)) || {}
  } catch (e) {
    data.value = {}
  } finally {
    loading.value = false
  }
}

const layoutOption = computed(() => {
  const names = layoutBoard.value.map(l => l.layout)
  return {
    color: ['#185fa5', '#0c447c'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['总套数', '已售'], bottom: 0 },
    grid: { left: 44, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, interval: 0 } },
    yAxis: { type: 'value' },
    series: [
      { name: '总套数', type: 'bar', barWidth: 22, data: layoutBoard.value.map(l => Number(l.total)) },
      { name: '已售', type: 'bar', barWidth: 22, data: layoutBoard.value.map(l => Number(l.sold)) }
    ]
  }
})

const priceOption = computed(() => {
  const periods = priceTrend.value.map(p => p.period)
  const values = priceTrend.value.map(p => Number(p.avgPrice))
  return {
    color: ['#185fa5'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toLocaleString('zh-CN') + ' 元/㎡' },
    grid: { left: 70, right: 24, top: 24, bottom: 30 },
    xAxis: { type: 'category', data: periods, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: '元/㎡' },
    series: [{ name: '成交均价', type: 'line', smooth: true, data: values, areaStyle: { opacity: 0.08 }, label: { show: true, position: 'top', formatter: p => Number(p.value).toLocaleString('zh-CN') } }]
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
.overview { max-width: 1560px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.hint { font-size: 12px; color: #909399; }
.section-card { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { font-size: 12px; color: #6b7280; display: flex; gap: 8px; }
.dot { width: 10px; height: 10px; border-radius: 3px; display: inline-block; margin-right: 4px; vertical-align: -1px; }
.dot.blue { background: #185fa5; }
.dot.gray { background: #e0e6ed; }
/* 进度区 */
.progress-wrap { display: grid; grid-template-columns: 260px 1fr 380px; gap: 20px; align-items: center; }
.progress-main { text-align: center; }
.pm-label { font-size: 13px; color: #6b7280; margin-bottom: 8px; }
.pm-rate { font-size: 44px; font-weight: 800; color: #185fa5; line-height: 1; font-variant-numeric: tabular-nums; }
.pm-rate span { font-size: 18px; color: #909399; }
.progress-mid .pm-legend { display: flex; gap: 18px; margin-top: 14px; font-size: 13px; color: #6b7280; flex-wrap: wrap; }
.progress-mid b { color: #1f2937; }
.progress-kpi { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.pk-item { display: flex; justify-content: space-between; align-items: baseline; background: #f8fafc; border-radius: 8px; padding: 10px 12px; font-size: 12.5px; color: #6b7280; }
.pk-item b { color: #1f2937; font-size: 14px; font-variant-numeric: tabular-nums; }
.c-blue { color: #185fa5 !important; }
.c-green { color: #67c23a !important; }
/* 楼栋卡片 */
.bld-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.bld-card { border: 1px solid #eef0f3; border-radius: 10px; padding: 14px; background: #fcfdfe; }
.bld-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.bld-code { font-size: 15px; font-weight: 700; color: #0c447c; }
.bld-meta { display: flex; justify-content: space-between; font-size: 12px; color: #6b7280; margin-top: 8px; }
.bld-value { display: flex; justify-content: space-between; font-size: 12px; color: #6b7280; margin-top: 4px; }
.bld-value b { color: #185fa5; }
</style>
