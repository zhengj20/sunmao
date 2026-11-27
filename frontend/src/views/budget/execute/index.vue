<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="year" style="width: 120px" @change="loadAll">
            <el-option v-for="y in years" :key="y" :label="y + '年度'" :value="y" />
          </el-select>
        </div>
        <div class="actions">
          <el-button @click="loadAll">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 年度执行总览 -->
    <el-row :gutter="12" class="stat-row">
      <el-col v-for="line in lines" :key="line.key" :span="8">
        <el-card shadow="never" class="line-card">
          <div class="line-head">
            <span class="line-title" :class="line.key">{{ line.label }}</span>
            <el-tag size="small" :type="line.rate >= 100 ? 'danger' : line.rate >= 80 ? 'warning' : 'success'">
              执行率 {{ line.rate }}%
            </el-tag>
          </div>
          <div class="line-grid">
            <div><div class="label">年度预算</div><div class="value num">{{ money(line.annual) }}</div></div>
            <div><div class="label">已分解</div><div class="value num">{{ money(line.allocated) }}</div></div>
            <div><div class="label">已执行</div><div class="value num">{{ money(line.actual) }}</div></div>
            <div><div class="label">未分解</div><div class="value num">{{ money(line.unallocated) }}</div></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 科目执行明细 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ year }}年度 预算执行明细（生效预算）</span>
          <span class="card-sub">成本执行 = 已审/已付付款按合同科目分摊；费用执行 = 费用预算占用 + 核销</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="byAccount" border stripe empty-text="暂无生效预算">
        <el-table-column prop="budgetCode" label="预算编码" width="150" />
        <el-table-column label="预算线" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'COST' ? 'primary' : 'success'">{{ budTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scopeName" label="项目/部门" min-width="140" show-overflow-tooltip />
        <el-table-column prop="accountName" label="科目" min-width="160" show-overflow-tooltip />
        <el-table-column label="控制方式" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" :type="controlTag(row.controlMode)">{{ row.controlMode || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="年度预算（元）" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.annual) }}</span></template>
        </el-table-column>
        <el-table-column label="已分解（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.allocated) }}</span></template>
        </el-table-column>
        <el-table-column label="已执行（元）" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.actual) }}</span></template>
        </el-table-column>
        <el-table-column label="执行率" width="90" align="center">
          <template #default="{ row }">
            <span class="num" :style="rateColor(row.rate)">{{ row.rate }}%</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 月度趋势 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ year }}年度 月度预算 vs 执行趋势</span>
          <span class="card-sub">柱 = 月度预算（成本线/费用线），线 = 实际执行</span>
        </div>
      </template>
      <ChartBox v-if="trend.length" :option="trendOption" height="320px" />
      <el-empty v-else description="暂无月度数据" :image-size="70" />
    </el-card>

    <!-- 月度资金计划执行 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ year }}年度 月度资金计划 vs 实际支付</span>
          <span class="card-sub">成本线资金计划 = 合同付款计划当月未付；费用线资金计划 = 当月费用预算；实际 = 成本付款单 / 费用占用+核销</span>
        </div>
      </template>
      <ChartBox v-if="fundMonths.length" :option="fundOption" height="300px" />
      <el-table :data="fundMonths" border size="small" show-summary :summary-method="fundSummary" class="fund-table"
        empty-text="暂无资金计划数据">
        <el-table-column prop="month" label="月份" width="70" align="center">
          <template #default="{ row }">{{ row.month }}月</template>
        </el-table-column>
        <el-table-column label="成本资金计划（元）" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.costPlan) }}</span></template>
        </el-table-column>
        <el-table-column label="成本实际（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.costActual) }}</span></template>
        </el-table-column>
        <el-table-column label="成本执行率" width="95" align="center">
          <template #default="{ row }"><span class="num" :style="rateColor(row.costRate)">{{ row.costRate }}%</span></template>
        </el-table-column>
        <el-table-column label="费用资金计划（元）" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.expensePlan) }}</span></template>
        </el-table-column>
        <el-table-column label="费用实际（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.expenseActual) }}</span></template>
        </el-table-column>
        <el-table-column label="费用执行率" width="95" align="center">
          <template #default="{ row }"><span class="num" :style="rateColor(row.expenseRate)">{{ row.expenseRate }}%</span></template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 管控链（B5） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">管控链：年度预算 → 月度预算 → 资金计划（B5）</span>
          <div class="head-actions">
            <el-select v-model="chainMonth" style="width: 110px" @change="loadChain">
              <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
            </el-select>
          </div>
        </div>
      </template>
      <el-alert v-if="chain && chain.over" type="error" :closable="false" class="mb"
        :title="`${chainMonth}月资金计划超出月度预算：成本线缺口 ${money(gapOf(chain.fund.cost))} 元，费用线缺口 ${money(gapOf(chain.fund.expense))} 元`" />
      <el-alert v-else-if="chain" type="success" :closable="false" class="mb"
        title="资金计划在月度预算范围内（B5 通过）" />
      <div v-if="chain" class="chain">
        <div class="chain-step">
          <div class="step-name">① 年度全面预算</div>
          <div class="step-row"><span>成本线</span><span class="num">{{ money(chain.annual.cost) }}</span></div>
          <div class="step-row"><span>费用线</span><span class="num">{{ money(chain.annual.expense) }}</span></div>
          <div class="step-row total"><span>合计</span><span class="num">{{ money(chain.annual.total) }}</span></div>
        </div>
        <div class="chain-arrow">控制</div>
        <div class="chain-step">
          <div class="step-name">② 月度全面预算（{{ chainMonth }}月）</div>
          <div class="step-row"><span>成本线</span><span class="num">{{ money(chain.monthly.cost) }}</span></div>
          <div class="step-row"><span>费用线</span><span class="num">{{ money(chain.monthly.expense) }}</span></div>
          <div class="step-row total"><span>合计</span><span class="num">{{ money(chain.monthly.total) }}</span></div>
        </div>
        <div class="chain-arrow">控制</div>
        <div class="chain-step">
          <div class="step-name">③ 资金计划（{{ chainMonth }}月）</div>
          <div class="step-row">
            <span>成本线（合同付款计划）</span>
            <span class="num">{{ money(chain.fund.cost.plan) }}<el-tag v-if="gapOf(chain.fund.cost) > 0" size="small" type="danger" class="gap-tag">缺口 {{ money(gapOf(chain.fund.cost)) }}</el-tag></span>
          </div>
          <div class="step-row">
            <span>费用线（当月费用预算）</span>
            <span class="num">{{ money(chain.fund.expense.plan) }}<el-tag v-if="gapOf(chain.fund.expense) > 0" size="small" type="danger" class="gap-tag">缺口 {{ money(gapOf(chain.fund.expense)) }}</el-tag></span>
          </div>
          <div class="step-row total"><span>合计</span><span class="num">{{ money(chain.fund.total) }}</span></div>
        </div>
      </div>
      <div v-else v-loading="chainLoading" class="chain-empty">加载中…</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { money } from '../../../utils/format'
import {
  budTypeLabel, controlTag, execOverview, execTrend, execChain, execFundMonths
} from '../../../api/budget'
import ChartBox from '../../../components/ChartBox.vue'

const curYear = new Date().getFullYear()
const years = [curYear - 1, curYear, curYear + 1]
const year = ref(curYear)
const chainMonth = ref(new Date().getMonth() + 1)

const loading = ref(false)
const overview = ref(null)
const trend = ref([])
const fundMonths = ref([])
const fundTotal = ref(null)
const chain = ref(null)
const chainLoading = ref(false)

const byAccount = computed(() => overview.value?.byAccount || [])

const num = v => Number(v) || 0
const lines = computed(() => {
  const ov = overview.value
  if (!ov) return []
  const norm = s => {
    const annual = num(s.annual), allocated = num(s.allocated)
    return {
      annual, allocated,
      actual: num(s.actual),
      rate: num(s.rate),
      unallocated: s.unallocated != null ? num(s.unallocated) : annual - allocated
    }
  }
  return [
    { key: 'cost', label: '成本线', ...norm(ov.cost) },
    { key: 'expense', label: '费用线', ...norm(ov.expense) },
    { key: 'total', label: '合计', ...norm(ov.total) }
  ]
})

const rateColor = r => (r >= 100 ? 'color:#c45656;font-weight:600' : r >= 80 ? 'color:#b88230' : '')
const gapOf = f => (f && f.gap != null && Number(f.gap) > 0 ? Number(f.gap) : 0)

// ===== 月度趋势图 =====
const trendOption = computed(() => {
  const ms = trend.value
  if (!ms.length) return {}
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['成本预算', '成本执行', '费用预算', '费用执行'], top: 0 },
    grid: { left: 70, right: 24, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: ms.map(m => m.month + '月') },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v >= 10000 ? (v / 10000) + '万' : v) } },
    series: [
      { name: '成本预算', type: 'bar', barWidth: 14, itemStyle: { color: '#7ba7d7' }, data: ms.map(m => num(m.costBudget)) },
      { name: '成本执行', type: 'line', smooth: true, symbolSize: 6, itemStyle: { color: '#185fa5' }, lineStyle: { width: 2 }, data: ms.map(m => num(m.costActual)) },
      { name: '费用预算', type: 'bar', barWidth: 14, itemStyle: { color: '#a3d9b1' }, data: ms.map(m => num(m.expenseBudget)) },
      { name: '费用执行', type: 'line', smooth: true, symbolSize: 6, itemStyle: { color: '#2f855a' }, lineStyle: { width: 2 }, data: ms.map(m => num(m.expenseActual)) }
    ]
  }
})

// ===== 月度资金计划执行 =====
const fundOption = computed(() => {
  const ms = fundMonths.value
  if (!ms.length) return {}
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['成本资金计划', '成本实际', '费用资金计划', '费用实际'], top: 0 },
    grid: { left: 70, right: 24, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: ms.map(m => m.month + '月') },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v >= 10000 ? (v / 10000) + '万' : v) } },
    series: [
      { name: '成本资金计划', type: 'bar', barWidth: 12, itemStyle: { color: '#7ba7d7' }, data: ms.map(m => num(m.costPlan)) },
      { name: '成本实际', type: 'bar', barWidth: 12, itemStyle: { color: '#185fa5' }, data: ms.map(m => num(m.costActual)) },
      { name: '费用资金计划', type: 'bar', barWidth: 12, itemStyle: { color: '#a3d9b1' }, data: ms.map(m => num(m.expensePlan)) },
      { name: '费用实际', type: 'bar', barWidth: 12, itemStyle: { color: '#2f855a' }, data: ms.map(m => num(m.expenseActual)) }
    ]
  }
})

const fundSummary = ({ columns }) => {
  const t = fundTotal.value || {}
  return columns.map(col => {
    if (col.label === '月份') return '合计'
    if (col.label.includes('成本资金计划')) return money(t.costPlan)
    if (col.label.includes('成本实际')) return money(t.costActual)
    if (col.label.includes('费用资金计划')) return money(t.expensePlan)
    if (col.label.includes('费用实际')) return money(t.expenseActual)
    return ''
  })
}

const loadOverview = async () => {
  loading.value = true
  try {
    overview.value = (await execOverview({ year: year.value }).catch(() => null)) || null
  } finally { loading.value = false }
}

const loadTrend = async () => {
  const r = (await execTrend({ year: year.value }).catch(() => null)) || null
  trend.value = r?.months || []
}

const loadFundMonths = async () => {
  const r = (await execFundMonths({ year: year.value }).catch(() => null)) || null
  fundMonths.value = r?.months || []
  fundTotal.value = r?.total || null
}

const loadChain = async () => {
  chainLoading.value = true
  try {
    chain.value = (await execChain({ year: year.value, month: chainMonth.value }).catch(() => null)) || null
  } finally { chainLoading.value = false }
}

const loadAll = () => {
  loadOverview()
  loadTrend()
  loadFundMonths()
  loadChain()
}

onMounted(loadAll)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; }
.stat-row { margin-bottom: 12px; }
.mb { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.fund-table { margin-top: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-title { font-weight: 600; }
.card-sub { font-size: 12px; color: #6b7280; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.line-title { font-size: 14px; font-weight: 600; }
.line-title.cost { color: #185fa5; }
.line-title.expense { color: #2f855a; }
.line-title.total { color: #374151; }
.line-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 12px; }
.line-grid .label { font-size: 12px; color: #6b7280; }
.line-grid .value { font-size: 17px; font-weight: 700; }
.chain { display: flex; align-items: stretch; gap: 8px; flex-wrap: wrap; }
.chain-step { flex: 1; min-width: 240px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; background: #fafbfc; }
.step-name { font-weight: 600; margin-bottom: 10px; }
.step-row { display: flex; justify-content: space-between; align-items: center; padding: 5px 0; font-size: 13px; }
.step-row span:first-child { color: #6b7280; }
.step-row.total { border-top: 1px dashed #d1d5db; margin-top: 4px; padding-top: 8px; font-weight: 600; }
.chain-arrow { align-self: center; font-size: 12px; color: #6b7280; font-weight: 600; }
.gap-tag { margin-left: 8px; }
</style>
