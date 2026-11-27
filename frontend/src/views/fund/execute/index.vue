<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-date-picker
            v-model="month"
            type="month"
            placeholder="执行月份"
            value-format="YYYY-MM"
            :clearable="false"
            style="width: 160px"
            @change="load"
          />
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>
    </el-card>

    <!-- 汇总指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">计划收款（万元）</div>
          <div class="value num">{{ wan(summary.receiptPlan) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">实际收款（万元）</div>
          <div class="value num" style="color:#67c23a">{{ wan(summary.receiptActual) }}</div>
          <div class="sub">完成率 {{ rate(summary.receiptActual, summary.receiptPlan) }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">计划付款（万元）</div>
          <div class="value num">{{ wan(summary.paymentPlan) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">实际付款（万元）</div>
          <div class="value num" style="color:#f56c6c">{{ wan(summary.paymentActual) }}</div>
          <div class="sub">执行率 {{ rate(summary.paymentActual, summary.paymentPlan) }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 对比图 -->
    <el-card shadow="never" class="stat-row">
      <template #header><span class="card-title">{{ month }} 收付执行对比（万元）</span></template>
      <ChartBox :option="chartOption" height="260px" />
    </el-card>

    <!-- 项目执行表 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">项目收付执行（元）</span>
          <el-tag size="small" effect="plain">定稿优先展示</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe show-summary :summary-method="rowSummary">
        <el-table-column label="项目" min-width="130">
          <template #default="{ row }">{{ row.projectName || `项目${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.versionType === 'FINAL' ? 'success' : 'info'">
              {{ row.versionType === 'FINAL' ? '定稿' : '初稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划收款" align="right" width="130">
          <template #default="{ row }"><span class="num">{{ money(row.receiptPlan) }}</span></template>
        </el-table-column>
        <el-table-column label="实际收款" align="right" width="130">
          <template #default="{ row }"><span class="num">{{ money(row.receiptActual) }}</span></template>
        </el-table-column>
        <el-table-column label="收款完成率" width="180">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.receiptRate)" :stroke-width="12" :color="progColor" />
          </template>
        </el-table-column>
        <el-table-column label="计划付款" align="right" width="130">
          <template #default="{ row }"><span class="num">{{ money(row.paymentPlan) }}</span></template>
        </el-table-column>
        <el-table-column label="实际付款" align="right" width="130">
          <template #default="{ row }"><span class="num">{{ money(row.paymentActual) }}</span></template>
        </el-table-column>
        <el-table-column label="付款执行率" width="180">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.paymentRate)" :stroke-width="12" :color="progColor" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 资金日报 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">资金日报（当日实付清单）</span>
          <el-date-picker
            v-model="dailyDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 160px"
            @change="loadDaily"
          />
        </div>
      </template>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">当日实付笔数</div>
            <div class="value num">{{ dailySummary.count ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">当日实付合计（万元）</div>
            <div class="value num">{{ wan(dailySummary.totalAmount) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">大额笔数</div>
            <div class="value num" style="color:#e6a23c">{{ dailySummary.largeCount ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">大额金额（万元）</div>
            <div class="value num" style="color:#f56c6c">{{ wan(dailySummary.largeAmount) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-table :data="dailyPayments" stripe>
        <el-table-column prop="paymentCode" label="付款单号" width="160" show-overflow-tooltip />
        <el-table-column label="项目" min-width="120">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="关联合同" width="110">
          <template #default="{ row }">
            <span v-if="row.contractId">HT-{{ row.contractId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="申请金额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.applyAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="实付金额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="大额标记" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isLarge === 1" size="small" type="danger" effect="light">大额</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="支付日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.paidDate) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { dailyReport, executeMonth, projectOptions } from '../../../api/fund'
import { fmtDate, money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const month = ref(dayjs().format('YYYY-MM'))
const dailyDate = ref(dayjs().format('YYYY-MM-DD'))
const loading = ref(false)
const rows = ref([])
const summary = ref({})
const dailyPayments = ref([])
const dailySummary = ref({})
const projects = ref([])

const projectName = id => projects.value.find(p => p.id === id)?.name || (id ? `项目${id}` : '-')

const rate = (a, p) => {
  const av = Number(a || 0), pv = Number(p || 0)
  return pv > 0 ? ((av / pv) * 100).toFixed(2) : '0.00'
}
const pct = v => Math.min(Number(v || 0), 100)
const progColor = [
  { color: '#f56c6c', percentage: 40 },
  { color: '#e6a23c', percentage: 70 },
  { color: '#67c23a', percentage: 100 }
]

const load = async () => {
  loading.value = true
  try {
    const data = await executeMonth(month.value)
    rows.value = data.rows || []
    summary.value = data.summary || {}
  } finally {
    loading.value = false
  }
}

const loadDaily = async () => {
  const data = await dailyReport(dailyDate.value)
  dailyPayments.value = data.payments || []
  dailySummary.value = data.summary || {}
}

const rowSummary = ({ columns }) => {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (i === 1) return ''
    const key = {
      计划收款: 'receiptPlan', 实际收款: 'receiptActual', 计划付款: 'paymentPlan', 实际付款: 'paymentActual'
    }[col.label]
    if (!key) return ''
    const sum = rows.value.reduce((s, r) => s + Number(r[key] || 0), 0)
    return money(sum)
  })
}

const chartOption = computed(() => {
  const names = rows.value.map(r => r.projectName || `项目${r.projectId}`)
  return {
    color: ['#8aadd2', '#67c23a', '#c3d6e8', '#f56c6c'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 80, right: 24, top: 36, bottom: 46 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'] },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '计划收款', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.value.map(r => Number(r.receiptPlan || 0)) },
      { name: '实际收款', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.value.map(r => Number(r.receiptActual || 0)) },
      { name: '计划付款', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.value.map(r => Number(r.paymentPlan || 0)) },
      { name: '实际付款', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.value.map(r => Number(r.paymentActual || 0)) }
    ]
  }
})

onMounted(async () => {
  projects.value = await projectOptions()
  load()
  loadDaily()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 20px; }
.stat-card .sub { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
