<template>
  <div class="page-container inv-overview">
    <!-- KPI 行 -->
    <div class="kpi-row" v-loading="loading">
      <div class="kpi-card">
        <div class="k-label">投资项目数</div>
        <div class="k-value num">{{ kpi.projectCount ?? '--' }}</div>
        <div class="k-foot">已建基线 {{ kpi.investedCount ?? 0 }} 个</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">投资总额（万元）</div>
        <div class="k-value num">{{ wan(kpi.totalInvestment) }}</div>
        <div class="k-foot">基线总成本（土地+开发）</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">货值总额（万元）</div>
        <div class="k-value num">{{ wan(kpi.totalSaleValue) }}</div>
        <div class="k-foot">基线总货值</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">预期净利润（万元）</div>
        <div class="k-value num" style="color:#67c23a">{{ wan(kpi.totalNetProfit) }}</div>
        <div class="k-foot">拿地测算基线</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">加权净利率</div>
        <div class="k-value num">{{ kpi.avgProfitRate != null ? kpi.avgProfitRate + '%' : '--' }}</div>
        <div class="k-foot">Σ净利润 ÷ Σ货值</div>
      </div>
      <div class="kpi-card" :class="kpi.profitDiff != null && kpi.profitDiff < 0 ? 'warn' : ''">
        <div class="k-label">最新动态利润（万元）</div>
        <div class="k-value num">{{ wan(kpi.totalDynamicProfit) }}</div>
        <div v-if="kpi.profitDiff != null" class="k-foot" :class="kpi.profitDiff < 0 ? 'bad' : 'good'">
          对比基线 {{ kpi.profitDiff >= 0 ? '+' : '' }}{{ wan(kpi.profitDiff) }} 万
        </div>
        <div v-else class="k-foot">暂无月度动态快照</div>
      </div>
    </div>

    <!-- 图表行 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">项目状态分布</span></template>
          <ChartBox v-if="statusDist.length" :option="statusOption" height="260px" />
          <el-empty v-else description="暂无项目数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">城市货值分布（基线）</span></template>
          <ChartBox v-if="cityValue.length" :option="cityOption" height="260px" />
          <el-empty v-else description="暂无基线数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">项目基线 vs 动态利润</span></template>
          <ChartBox v-if="profitCompare.length" :option="compareOption" height="260px" />
          <el-empty v-else description="暂无利润数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 项目投资清单 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">项目投资清单（基线测算 vs 最新动态）</span>
          <span class="legend">点击「后评估」查看量/价/成本三因素差异分解</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="projects" stripe empty-text="暂无项目数据">
        <el-table-column label="项目" min-width="150">
          <template #default="{ row }">
            <div class="proj-name">{{ row.projectName }}</div>
            <div class="proj-sub">{{ row.city || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="基线版本" width="160">
          <template #default="{ row }">
            <span v-if="row.baseline" class="baseline">
              {{ row.baseline.versionCode }}
              <el-tag size="small" :type="row.baseline.locked === 1 ? 'success' : 'info'" effect="plain" style="margin-left:4px">
                {{ row.baseline.locked === 1 ? '已锁定' : '草稿' }}
              </el-tag>
            </span>
            <span v-else class="muted">未建立</span>
          </template>
        </el-table-column>
        <el-table-column label="总货值(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.baseline?.saleValue) }}</span></template>
        </el-table-column>
        <el-table-column label="总成本(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.baseline?.totalCost) }}</span></template>
        </el-table-column>
        <el-table-column label="预期净利(元)" width="140" align="right">
          <template #default="{ row }"><span class="num" style="color:#67c23a">{{ money(row.baseline?.netProfit) }}</span></template>
        </el-table-column>
        <el-table-column label="净利率" width="80" align="right">
          <template #default="{ row }">{{ row.baseline?.profitRate != null ? row.baseline.profitRate + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="IRR" width="80" align="right">
          <template #default="{ row }">{{ row.baseline?.irr != null ? row.baseline.irr + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="回收期" width="80" align="right">
          <template #default="{ row }">{{ row.baseline?.paybackYears != null ? row.baseline.paybackYears + '年' : '-' }}</template>
        </el-table-column>
        <el-table-column label="动态利润(元)" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.dynamic?.dynamicProfit) }}</span>
            <div class="proj-sub" v-if="row.dynamic?.snapMonth">{{ row.dynamic.snapMonth }}</div>
          </template>
        </el-table-column>
        <el-table-column label="利润差异(元)" width="140" align="right">
          <template #default="{ row }">
            <span v-if="row.profitDiff != null" class="num" :class="row.profitDiff < 0 ? 'bad' : 'good'">
              {{ row.profitDiff > 0 ? '+' : '' }}{{ money(row.profitDiff) }}
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goPostEval(row)">后评估</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 快捷入口 -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="card-title">快捷入口</span></template>
      <div class="entry-grid">
        <div v-for="e in entries" :key="e.path" class="entry-item" @click="go(e.path)">
          <el-icon class="entry-icon"><component :is="e.icon" /></el-icon>
          <div class="entry-text">
            <div class="entry-name">{{ e.name }}</div>
            <div class="entry-desc">{{ e.desc }}</div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Coin, Money, Odometer, TrendCharts } from '@element-plus/icons-vue'
import { DICTS, dictLabel, money, wan } from '../../../utils/format'
import { investOverview } from '../../../api/invest'
import ChartBox from '../../../components/ChartBox.vue'

const router = useRouter()
const go = path => router.push(path)

const loading = ref(false)
const data = ref(null)

const kpi = computed(() => {
  const k = data.value?.kpi || {}
  return {
    projectCount: k.projectCount ?? 0,
    investedCount: k.investedCount ?? 0,
    totalInvestment: Number(k.totalInvestment) || 0,
    totalSaleValue: Number(k.totalSaleValue) || 0,
    totalNetProfit: Number(k.totalNetProfit) || 0,
    avgProfitRate: k.avgProfitRate != null ? Number(k.avgProfitRate) : null,
    totalDynamicProfit: Number(k.totalDynamicProfit) || 0,
    profitDiff: k.profitDiff != null ? Number(k.profitDiff) : null
  }
})
const projects = computed(() => data.value?.projects || [])
const statusDist = computed(() => data.value?.statusDist || [])
const cityValue = computed(() => data.value?.cityValue || [])

const statusLabel = s => dictLabel('project_status', s)

const profitCompare = computed(() =>
  projects.value
    .filter(r => r.baseline?.netProfit != null || r.dynamic?.dynamicProfit != null)
    .map(r => ({
      name: r.projectName,
      baseline: Number(r.baseline?.netProfit) || 0,
      dynamic: Number(r.dynamic?.dynamicProfit) || 0
    }))
)

// 状态分布（环形）
const statusOption = computed(() => ({
  color: ['#185fa5', '#e6a23c', '#67c23a', '#909399'],
  tooltip: { trigger: 'item', formatter: p => `${p.name}: ${p.value} 个 (${p.percent}%)` },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  series: [{
    type: 'pie', radius: ['46%', '72%'], center: ['50%', '44%'], label: { show: false },
    data: statusDist.value.map(d => ({ name: statusLabel(d.name), value: Number(d.value) || 0 }))
  }]
}))

// 城市货值（柱状）
const cityOption = computed(() => ({
  color: ['#185fa5'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  grid: { left: 60, right: 24, top: 24, bottom: 30 },
  xAxis: { type: 'category', data: cityValue.value.map(d => d.name), axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 1e4).toFixed(0) + '万' } },
  series: [{ type: 'bar', barWidth: 36, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: cityValue.value.map(d => Number(d.value) || 0) }]
}))

// 基线 vs 动态（分组柱状）
const compareOption = computed(() => ({
  color: ['#8aadd2', '#185fa5'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 60, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: profitCompare.value.map(d => d.name), axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 1e4).toFixed(0) + '万' } },
  series: [
    { name: '基线净利', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: profitCompare.value.map(d => d.baseline) },
    { name: '动态利润', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: profitCompare.value.map(d => d.dynamic) }
  ]
}))

const entries = [
  { name: '投资测算', desc: '版本管理 / 锁定基线 / IRR 重算', path: '/invest/calc', icon: TrendCharts },
  { name: '货值台账', desc: '业态 × 面积 × 价格版本', path: '/invest/value', icon: Coin },
  { name: '动态利润', desc: '月度快照 / 基线对比 / 刷新', path: '/invest/profit', icon: Money },
  { name: '投资驾驶舱', desc: 'KPI + 对比 + 趋势 + 预警', path: '/invest/cockpit', icon: Odometer }
]

// 后评估：跳转投资测算页（版本列表内含后评估入口）
const goPostEval = row => {
  router.push({ path: '/invest/calc', query: { projectId: row.projectId } })
}

onMounted(async () => {
  loading.value = true
  try {
    data.value = await investOverview()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.inv-overview { max-width: 1560px; }
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-card { background: #fff; border: 1px solid #eef0f3; border-radius: 10px; padding: 14px 16px; }
.kpi-card.warn { border-color: #fbc4c4; background: #fef6f6; }
.k-label { font-size: 12px; color: #6b7280; margin-bottom: 8px; }
.k-value { font-size: 21px; font-weight: 700; color: #111827; }
.k-foot { font-size: 11.5px; color: #9ca3af; margin-top: 6px; }
.k-foot.bad { color: #f56c6c; }
.k-foot.good { color: #67c23a; }
.section-row { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.legend { font-size: 12px; color: #6b7280; }
.proj-name { font-weight: 600; }
.proj-sub { font-size: 11px; color: #9ca3af; }
.baseline { font-size: 12px; }
.muted { color: #c0c4cc; }
.num { font-variant-numeric: tabular-nums; }
.good { color: #67c23a; }
.bad { color: #f56c6c; }
.entry-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.entry-item { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid #eef0f3; border-radius: 8px; cursor: pointer; transition: all .15s; }
.entry-item:hover { border-color: #185fa5; background: #f5f9fd; }
.entry-icon { font-size: 22px; color: #185fa5; }
.entry-name { font-size: 13px; font-weight: 600; }
.entry-desc { font-size: 11.5px; color: #9ca3af; margin-top: 2px; }
</style>
