<template>
  <div class="page-container cockpit-project">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 260px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <div v-if="project.id" class="proj-meta">
          <el-tag size="small" effect="plain">{{ project.projectCode || '-' }}</el-tag>
          <el-tag size="small" effect="plain">{{ project.orgName || '未分配' }}</el-tag>
          <el-tag size="small" effect="plain">{{ project.city || '-' }}</el-tag>
          <el-tag size="small" :type="projectStatusType(project.status)">{{ dictLabel('project_status', project.status) }}</el-tag>
          <span class="muted">拿地 {{ fmtDate(project.landAcquiredDate) }}</span>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!loaded || !projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 主项计划地铁图 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span class="card-title">主项计划地铁图（{{ (plan.subway || []).length }} 个里程碑）</span>
            <div class="legend">
              <span><i class="dot green" />已完成</span>
              <span><i class="dot blue" />进行中</span>
              <span><i class="dot red" />滞后</span>
              <span><i class="dot yellow" />预警</span>
              <span><i class="dot gray" />未开始</span>
            </div>
          </div>
        </template>
        <SubwayMap v-if="(plan.subway || []).length" :stations="plan.subway" :overall-pct="Number(plan.kpi?.overallProgress) || 0" :clickable="false" />
        <el-empty v-else description="暂无里程碑" :image-size="70" />
      </el-card>

      <!-- 专项计划进度 + 施工进度 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">专项计划进度（按责任条线）</span></template>
            <SpecialtyProgress :rows="plan.specialty || []" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">施工进度（楼栋 × 部位 · 已确认形象进度）</span></template>
            <ConstructionMatrix :rows="plan.construction || []" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 供销存瀑布 + 动态利润 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><span class="card-title">货值供销存瀑布（供货 - 已售 - 退房 = 在手）</span></template>
            <ChartBox v-if="hasWaterfall" :option="waterfallOption" height="320px" />
            <el-empty v-else description="暂无货值流水" :image-size="80" />
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="card-title">动态利润拆解（货值-成本-三费-税金）</span>
                <el-button link type="primary" @click="openDrill">查看明细</el-button>
              </div>
            </template>
            <div v-if="dp.snapMonth" class="dp-stats">
              <div class="dp-item"><span>动态货值</span><b>{{ wan(dp.dynamicValue) }}万</b></div>
              <div class="dp-item"><span>动态成本</span><b>{{ wan(dp.dynamicCost) }}万</b></div>
              <div class="dp-item"><span>三费</span><b>{{ wan(dp.threeFee) }}万</b></div>
              <div class="dp-item"><span>税金</span><b>{{ wan(dp.taxAmount) }}万</b></div>
              <div class="dp-item total"><span>动态利润</span><b>{{ wan(dp.dynamicProfit) }}万</b></div>
              <div class="dp-item"><span>利润率</span><b>{{ pct(dp.profitRate) }}</b></div>
              <div class="dp-item"><span>测算基线</span><b>{{ wan(dp.baselineProfit) }}万</b></div>
              <div class="dp-item muted">月份 {{ dp.snapMonth }}</div>
            </div>
            <ChartBox :option="dpOption" height="240px" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 三算 + 红黄灯 + 三台账 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">三算对比（目标/动态/实际）</span></template>
            <ChartBox :option="threeCalcOption" height="260px" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">计划红黄灯分布</span></template>
            <ChartBox v-if="alertDistribution.length" :option="alertOption" height="260px" />
            <el-empty v-else description="暂无计划任务" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">三台账速览（合同/变更/付款）</span></template>
            <div class="ledger">
              <div class="ledger-item">
                <div class="l-name">合同台账</div>
                <div class="l-cnt">{{ ledger.contract?.cnt ?? 0 }} 笔</div>
                <div class="l-amt">{{ wan(ledger.contract?.amount) }} 万</div>
              </div>
              <div class="ledger-item">
                <div class="l-name">变更台账</div>
                <div class="l-cnt">{{ ledger.change?.cnt ?? 0 }} 笔</div>
                <div class="l-amt">{{ wan(ledger.change?.amount) }} 万</div>
              </div>
              <div class="ledger-item">
                <div class="l-name">付款台账</div>
                <div class="l-cnt">{{ ledger.payment?.cnt ?? 0 }} 笔</div>
                <div class="l-amt">{{ wan(ledger.payment?.amount) }} 万</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 货值台账 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">货值台账（按业态）</span></template>
        <el-table v-loading="loading" :data="byCategory" stripe size="small" empty-text="暂无货值台账">
          <el-table-column prop="productCategory" label="业态" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.productCategory || '-' }}</template>
          </el-table-column>
          <el-table-column label="面积(㎡)" width="120" align="right"><template #default="{ row }"><span class="num">{{ money(row.area) }}</span></template></el-table-column>
          <el-table-column label="单价(元/㎡)" width="130" align="right"><template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template></el-table-column>
          <el-table-column label="货值(万)" width="130" align="right"><template #default="{ row }"><span class="num">{{ wan(row.totalValue) }}</span></template></el-table-column>
          <el-table-column label="供货日期" width="120" align="center"><template #default="{ row }">{{ fmtDate(row.supplyDate) }}</template></el-table-column>
        </el-table>
      </el-card>

      <!-- 下钻弹窗 -->
      <DrillDialog
        v-model="drillVisible"
        metric-code="DYNAMIC_PROFIT"
        title="动态利润明细"
        :params="{ projectId }"
      />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { cockpitProject } from '../../../api/cockpit'
import { projectOptions } from '../../../api/mdm'
import { planOverview } from '../../../api/plan'
import { wan, money, fmtDate, dictLabel } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'
import DrillDialog from '../components/DrillDialog.vue'
import SubwayMap from '../../../components/plan/SubwayMap.vue'
import SpecialtyProgress from '../../../components/plan/SpecialtyProgress.vue'
import ConstructionMatrix from '../../../components/plan/ConstructionMatrix.vue'

const route = useRoute()

const loading = ref(false)
const loaded = ref(false)
const projects = ref([])
const projectId = ref(null)
const project = ref({})
const plan = ref({})
const valueWaterfall = ref({})
const dp = ref({})
const threeCalc = ref({})
const alertDistribution = ref([])
const ledger = ref({})
const byCategory = ref([])
const drillVisible = ref(false)

const openDrill = () => { drillVisible.value = true }

const pct = v => (v == null || v === '' ? '-' : Number(v).toFixed(1) + '%')
const projectStatusType = s => (s === 'CONSTRUCTION' ? 'warning' : s === 'DELIVERED' ? 'success' : 'info')

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [d, ov] = await Promise.all([
      cockpitProject(projectId.value),
      planOverview(projectId.value).catch(() => null)
    ])
    project.value = d.project || {}
    plan.value = ov || {}
    valueWaterfall.value = d.valueWaterfall || {}
    dp.value = d.dynamicProfit || {}
    threeCalc.value = d.threeCalc || {}
    alertDistribution.value = d.alertDistribution || []
    ledger.value = d.ledger || {}
    byCategory.value = d.valueWaterfall?.byCategory || []
    loaded.value = true
  } finally {
    loading.value = false
  }
}

// ===== 供销存瀑布 =====
const hasWaterfall = computed(() =>
  Number(valueWaterfall.value.totalSupply) > 0 ||
  Number(valueWaterfall.value.totalSold) > 0 ||
  Number(valueWaterfall.value.totalRefund) > 0
)

const waterfallOption = computed(() => {
  const v = valueWaterfall.value || {}
  const supply = Number(v.totalSupply || 0)
  const sold = Number(v.totalSold || 0)
  const refund = Number(v.totalRefund || 0)
  const end = supply - sold - refund
  const items = [
    { name: '供货货值', type: 'add', val: supply },
    { name: '已售', type: 'sub', val: sold },
    { name: '退房', type: 'sub', val: refund },
    { name: '在手货值', type: 'total', val: end }
  ]
  let running = 0
  const base = [], bar = []
  for (const it of items) {
    if (it.type === 'add') { base.push(0); bar.push(it.val); running += it.val }
    else if (it.type === 'sub') { running -= it.val; base.push(running); bar.push(it.val) }
    else { base.push(0); bar.push(Math.abs(end)) }
  }
  const colors = { 供货货值: '#185fa5', 已售: '#f56c6c', 退房: '#e6a23c', 在手货值: '#67c23a' }
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: val => wan(val) + ' 万元' },
    grid: { left: 80, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: items.map(i => i.name), axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', axisLabel: { formatter: val => (val / 10000).toFixed(0) + '万' } },
    series: [
      { name: 'base', type: 'bar', stack: 'wf', itemStyle: { color: 'transparent', borderColor: 'transparent' }, emphasis: { itemStyle: { color: 'transparent' } }, data: base },
      {
        name: '货值', type: 'bar', stack: 'wf', barWidth: 56,
        data: items.map((it, idx) => ({ value: bar[idx], itemStyle: { color: colors[it.name], borderRadius: 4 } })),
        label: { show: true, position: 'top', formatter: p => wan(p.value) }
      }
    ]
  }
})

// ===== 动态利润拆解 =====
const dpOption = computed(() => {
  const d = dp.value || {}
  return {
    color: ['#185fa5', '#f56c6c', '#e6a23c', '#909399', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 10 },
    grid: { left: 70, right: 20, top: 20, bottom: 44 },
    xAxis: { type: 'category', data: ['动态货值', '动态成本', '三费', '税金', '动态利润'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{
      name: '金额', type: 'bar', barWidth: 34,
      data: [
        Number(d.dynamicValue || 0), -Number(d.dynamicCost || 0), -Number(d.threeFee || 0), -Number(d.taxAmount || 0), Number(d.dynamicProfit || 0)
      ],
      label: { show: true, position: 'top', formatter: p => wan(Math.abs(p.value)) }
    }]
  }
})

// ===== 三算对比 =====
const threeCalcOption = computed(() => {
  const t = threeCalc.value || {}
  return {
    color: ['#8aadd2', '#185fa5', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 10 },
    grid: { left: 70, right: 20, top: 20, bottom: 44 },
    xAxis: { type: 'category', data: ['目标成本', '动态成本', '实际成本'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{
      name: '金额', type: 'bar', barWidth: 30,
      data: [Number(t.targetCost || 0), Number(t.dynamicCost || 0), Number(t.actualCost || 0)],
      label: { show: true, position: 'top', formatter: p => wan(p.value) }
    }]
  }
})

// ===== 红黄灯分布 =====
const ALERT_META = { RED: '红灯', YELLOW: '黄灯', GREEN: '绿灯', GRAY: '未标' }
const ALERT_COLOR = { RED: '#f56c6c', YELLOW: '#e6a23c', GREEN: '#67c23a' }
const alertOption = computed(() => {
  const data = alertDistribution.value.map(a => ({
    name: ALERT_META[a.alertStatus] || a.alertStatus || '其他',
    value: Number(a.cnt || 0),
    itemStyle: { color: ALERT_COLOR[a.alertStatus] || '#c0c4cc' }
  }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 8 },
    series: [{
      name: '红黄灯', type: 'pie', radius: ['45%', '70%'], center: ['50%', '46%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}' },
      data
    }]
  }
})

onMounted(async () => {
  projects.value = await projectOptions().catch(() => [])
  const qId = route.query.projectId
  if (qId) projectId.value = Number(qId)
  else if (projects.value.length) projectId.value = projects.value[0].id
  if (projectId.value) load()
})
</script>

<style scoped>
.cockpit-project { max-width: 1560px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.proj-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.section-card { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-right: 4px; vertical-align: -1px; }
.dot.green { background: #67c23a; }
.dot.yellow { background: #e6a23c; }
.dot.red { background: #f56c6c; }
.dot.blue { background: #185fa5; }
.dot.gray { background: #fff; border: 2px solid #c0c4cc; }
/* 动态利润 */
.dp-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; padding-bottom: 8px; margin-bottom: 8px; }
.dp-item { display: flex; justify-content: space-between; font-size: 12.5px; color: #4b5563; padding: 4px 6px; background: #f8fafc; border-radius: 6px; }
.dp-item b { color: #1f2937; font-variant-numeric: tabular-nums; }
.dp-item.total { background: #f0f9ec; }
.dp-item.total b { color: #67c23a; font-weight: 700; }
.dp-item.muted { background: transparent; color: #909399; }
/* 三台账 */
.ledger { display: flex; flex-direction: column; gap: 12px; padding-top: 6px; }
.ledger-item { border: 1px solid #eef0f3; border-radius: 8px; padding: 12px 14px; display: flex; align-items: baseline; justify-content: space-between; }
.l-name { font-size: 13px; color: #4b5563; font-weight: 600; }
.l-cnt { font-size: 13px; color: #185fa5; font-weight: 700; }
.l-amt { font-size: 14px; color: #1f2937; font-weight: 700; font-variant-numeric: tabular-nums; }
.num { font-variant-numeric: tabular-nums; }
</style>
