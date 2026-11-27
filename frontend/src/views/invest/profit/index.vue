<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select
            v-model="projectId"
            placeholder="请选择项目"
            filterable
            style="width: 220px"
            @change="load"
          >
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
        <el-button type="warning" :icon="Refresh" :loading="refreshing" @click="onRefresh">
          刷新动态利润快照
        </el-button>
      </div>
    </el-card>

    <template v-if="latest">
      <!-- 指标卡 -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">动态货值（万元）</div>
            <div class="value num">{{ wan(latest.dynamicValue) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">动态成本（万元）</div>
            <div class="value num">{{ wan(latest.dynamicCost) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">动态利润（万元）</div>
            <div class="value num" style="color:#67c23a">{{ wan(latest.dynamicProfit) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">动态利润率</div>
            <div class="value num">{{ latest.profitRate != null ? latest.profitRate + '%' : '-' }}</div>
            <div class="sub">快照月份：{{ latest.snapMonth || '-' }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="8">
          <el-card shadow="never" class="stat-card">
            <div class="label">拿地基线净利润（万元）</div>
            <div class="value num">{{ wan(baseline.netProfit) }}</div>
            <div class="sub">基准版本：{{ baseline.versionCode || '暂无锁定版本' }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="stat-card">
            <div class="label">基线净利率</div>
            <div class="value num">{{ baseline.profitRate != null ? baseline.profitRate + '%' : '-' }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="stat-card" :class="{ 'diff-card': true }">
            <div class="label">利润差异（动态 − 基线）</div>
            <div class="value num" :style="{ color: profitDiff < 0 ? '#f56c6c' : '#67c23a' }">
              {{ profitDiff == null ? '-' : (profitDiff > 0 ? '+' : '') + wan(profitDiff) }}
            </div>
            <div class="sub">{{ profitDiff == null ? '暂无可对比基线' : (profitDiff >= 0 ? '优于拿地测算' : '低于拿地测算') }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">拿地基线 vs 动态净利润（万元）</span></template>
            <ChartBox :option="compareOption" height="280px" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">动态货值利润构成（万元）</span></template>
            <ChartBox :option="composeOption" height="280px" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 动态明细 -->
      <el-card shadow="never" class="table-card">
        <template #header><span class="card-title">动态利润测算明细</span></template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="动态货值(元)"><span class="num">{{ money(latest.dynamicValue) }}</span></el-descriptions-item>
          <el-descriptions-item label="动态成本(元)"><span class="num">{{ money(latest.dynamicCost) }}</span></el-descriptions-item>
          <el-descriptions-item label="动态三费(元)"><span class="num">{{ money(latest.threeFee) }}</span></el-descriptions-item>
          <el-descriptions-item label="税金(元)"><span class="num">{{ money(latest.taxAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="动态利润(元)"><span class="num">{{ money(latest.dynamicProfit) }}</span></el-descriptions-item>
          <el-descriptions-item label="基线利润(元)"><span class="num">{{ money(latest.baselineProfit) }}</span></el-descriptions-item>
        </el-descriptions>
        <div class="formula">动态利润 = 动态货值 − 动态成本 − 三费 − 税金；三费/税金按集团费率参数演示计算，成本确认事件后自动联动重算</div>
      </el-card>
    </template>

    <el-card v-else-if="!loading" shadow="never" class="table-card">
      <el-empty :description="projectId ? '该项目暂无动态利润快照，请点击右上角刷新生成' : '请先选择项目'" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { profitLatest, profitRefresh, projectOptions } from '../../../api/invest'
import { money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const BLUE = ['#185fa5', '#2a74bd', '#5687ba', '#8aadd2', '#c3d6e8', '#124a80']

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const refreshing = ref(false)
const latest = ref(null)
const baseline = ref({})
const diff = ref(null)

const profitDiff = computed(() => (diff.value ? diff.value.profitDiff : null))

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const data = await profitLatest(projectId.value)
    latest.value = data.latest
    baseline.value = data.baseline || {}
    diff.value = data.diff
  } finally {
    loading.value = false
  }
}

const onRefresh = async () => {
  if (!projectId.value) return ElMessage.warning('请先选择项目')
  refreshing.value = true
  try {
    await profitRefresh(projectId.value)
    ElMessage.success('动态利润快照已刷新')
    load()
  } finally {
    refreshing.value = false
  }
}

const compareOption = computed(() => ({
  color: ['#8aadd2', '#185fa5'],
  tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
  grid: { left: 60, right: 24, top: 40, bottom: 30 },
  xAxis: { type: 'category', data: ['拿地基线', '当前动态'], axisLabel: { fontSize: 13 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    {
      name: '净利润',
      type: 'bar',
      barWidth: 56,
      data: [
        { value: Number(baseline.value.netProfit || 0), itemStyle: { borderRadius: [6, 6, 0, 0] } },
        { value: Number(latest.value?.dynamicProfit || 0), itemStyle: { borderRadius: [6, 6, 0, 0] } }
      ],
      label: { show: true, position: 'top', formatter: p => wan(p.value) }
    }
  ]
}))

const composeOption = computed(() => {
  const l = latest.value || {}
  const names = ['动态货值', '动态成本', '三费', '税金', '动态净利润']
  const values = [l.dynamicValue, l.dynamicCost, l.threeFee, l.taxAmount, l.dynamicProfit].map(v => Number(v || 0))
  return {
    color: [BLUE[0], '#e6a23c', '#8aadd2', '#c3d6e8', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    grid: { left: 90, right: 50, top: 30, bottom: 30 },
    xAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    yAxis: { type: 'category', data: names, inverse: true, axisLabel: { fontSize: 12 } },
    series: [
      {
        name: '金额',
        type: 'bar',
        barWidth: 18,
        data: values,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', formatter: p => wan(p.value) }
      }
    ]
  }
})

onMounted(async () => {
  projects.value = await projectOptions()
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; }
.stat-card .sub { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.formula { font-size: 12px; color: #9ca3af; margin-top: 12px; }
</style>
