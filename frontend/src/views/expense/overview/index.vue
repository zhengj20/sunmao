<template>
  <div class="page-container exp-overview">
    <!-- KPI 行 -->
    <div class="kpi-row" v-loading="loading">
      <div class="kpi-card">
        <div class="k-label">预算余额（元）</div>
        <div class="k-value num" :class="budget.balance < 0 ? 'bad' : ''">{{ wan(budget.balance) }}</div>
        <div class="k-foot">总预算 {{ wan(budget.budget) }} / 已核销 {{ wan(budget.writeoff) }}</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">最近月支出（元）</div>
        <div class="k-value num">{{ monthExpense ? wan(monthExpense.amt) : '--' }}</div>
        <div class="k-foot">{{ monthExpense ? monthExpense.month + ' · ' + monthExpense.cnt + ' 单' : '暂无数据' }}</div>
      </div>
      <div class="kpi-card" :class="pendingCount ? 'warn' : ''">
        <div class="k-label">审批中单据</div>
        <div class="k-value num">{{ pendingCount }}</div>
        <div class="k-foot">申请 {{ counts.applyApproving }} / 报销 {{ counts.reimbApproving }}</div>
      </div>
      <div class="kpi-card" :class="counts.loanActive ? 'warn' : ''">
        <div class="k-label">在借借款</div>
        <div class="k-value num">{{ counts.loanActive }}</div>
        <div class="k-foot">含逾期，需关注冲销</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">待付付款单</div>
        <div class="k-value num">{{ counts.paymentDraft }}</div>
        <div class="k-foot">待实付登记</div>
      </div>
      <div class="kpi-card">
        <div class="k-label">未验真发票</div>
        <div class="k-value num">{{ counts.invoiceNew }}</div>
        <div class="k-foot">发票池待验真</div>
      </div>
    </div>

    <!-- 图表行 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">部门预算执行 Top5（预算 / 占用 / 核销）</span></template>
          <ChartBox v-if="topDept.length" :option="deptOption" height="280px" />
          <el-empty v-else description="暂无预算数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">月度费用支出趋势（近 6 月）</span></template>
          <ChartBox v-if="trend.length" :option="trendOption" height="280px" />
          <el-empty v-else description="暂无支出数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Wallet, Document, Tickets, Money, CreditCard, Files,
  PieChart, DataLine, Stamp, Coin
} from '@element-plus/icons-vue'
import { wan } from '../../../utils/format'
import { reportOverview } from '../../../api/expense'
import ChartBox from '../../../components/ChartBox.vue'

const router = useRouter()
const go = path => router.push(path)

const loading = ref(false)
const budget = reactive({ budget: 0, occupy: 0, writeoff: 0, balance: 0 })
const counts = reactive({ applyApproving: 0, reimbApproving: 0, loanActive: 0, paymentDraft: 0, invoiceNew: 0 })
const monthExpense = ref(null)
const trend = ref([])
const topDept = ref([])

const pendingCount = computed(() => Number(counts.applyApproving) + Number(counts.reimbApproving))

const deptOption = computed(() => ({
  color: ['#185fa5', '#e6a23c', '#67c23a'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  grid: { left: 60, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: topDept.value.map(d => d.deptName), axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    { name: '预算', type: 'bar', barWidth: 14, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: topDept.value.map(d => Number(d.budget) || 0) },
    { name: '占用', type: 'bar', barWidth: 14, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: topDept.value.map(d => Number(d.occupy) || 0) },
    { name: '核销', type: 'bar', barWidth: 14, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: topDept.value.map(d => Number(d.writeoff) || 0) }
  ]
}))

const trendOption = computed(() => ({
  color: ['#185fa5'],
  tooltip: { trigger: 'axis', valueFormatter: v => Number(v || 0).toLocaleString() + ' 元' },
  grid: { left: 60, right: 24, top: 24, bottom: 30 },
  xAxis: { type: 'category', data: trend.value.map(t => t.month), boundaryGap: false },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [{ type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.08 }, data: trend.value.map(t => Number(t.amt) || 0) }]
}))

const entries = [
  { name: '费用预算', desc: '部门×科目×月四栏台账', path: '/expense/budget', icon: PieChart },
  { name: '费控标准', desc: '职级×城市限额标准', path: '/expense/standard', icon: Files },
  { name: '事项申请', desc: '通用事项审批（部门→财务）', path: '/expense/matter', icon: Document },
  { name: '费用申请', desc: '事前金额申请 + 预算占用', path: '/expense/apply', icon: Stamp },
  { name: '费用合同', desc: '合同台账 + 付款跟踪', path: '/expense/contract', icon: Tickets },
  { name: '借款管理', desc: '额度控制 / 冲销', path: '/expense/loan', icon: Money },
  { name: '报销管理', desc: '报销单 + 超标特批', path: '/expense/reimburse', icon: CreditCard },
  { name: '费用付款', desc: '付款单 / 实付登记', path: '/expense/payment', icon: Wallet },
  { name: '发票池', desc: '全集团进项 + 查重', path: '/expense/invoice', icon: Files },
  { name: '费用分析', desc: '三费占比 / 费效比 / 异常', path: '/expense/report', icon: DataLine },
  { name: '费用驾驶舱', desc: 'KPI + 趋势 + 预警', path: '/expense/cockpit', icon: DataLine },
  { name: '费用分摊', desc: '月度分摊计算', path: '/expense/allocation', icon: Coin }
]

const load = async () => {
  loading.value = true
  try {
    const r = await reportOverview().catch(() => null)
    if (!r) return
    Object.assign(budget, { budget: 0, occupy: 0, writeoff: 0, balance: 0 }, r.budget || {})
    Object.assign(counts, { applyApproving: 0, reimbApproving: 0, loanActive: 0, paymentDraft: 0, invoiceNew: 0 }, r.counts || {})
    monthExpense.value = r.monthExpense || null
    trend.value = r.trend || []
    topDept.value = r.topDept || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.exp-overview { max-width: 1560px; }
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-card { background: #fff; border: 1px solid #eef0f3; border-radius: 10px; padding: 16px; }
.kpi-card.warn { border-color: #f3d19e; background: #fffaf0; }
.k-label { font-size: 12px; color: #6b7280; margin-bottom: 8px; }
.k-value { font-size: 24px; font-weight: 700; color: #0c447c; }
.k-value.bad { color: #c45656; }
.k-foot { font-size: 11.5px; color: #909399; margin-top: 6px; }
.section-row { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.entry-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; }
.entry-item { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid #eef0f3; border-radius: 8px; cursor: pointer; transition: all .15s; }
.entry-item:hover { border-color: #185fa5; background: #f5f9fd; }
.entry-icon { font-size: 22px; color: #185fa5; }
.entry-name { font-size: 13px; font-weight: 600; color: #1f2937; }
.entry-desc { font-size: 11.5px; color: #909399; margin-top: 2px; }
.num { font-variant-numeric: tabular-nums; }
@media (max-width: 1200px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
  .entry-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
