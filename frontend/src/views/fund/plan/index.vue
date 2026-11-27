<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-date-picker
            v-model="month"
            type="month"
            placeholder="选择月份"
            value-format="YYYY-MM"
            :clearable="false"
            style="width: 160px"
            @change="load"
          />
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
        <div>
          <el-button type="warning" :icon="MagicStick" :loading="generating" @click="onGenerate">
            自动汇总生成
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 汇总指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="5">
        <el-card shadow="never" class="stat-card">
          <div class="label">计划收款（万元）</div>
          <div class="value num" style="color:#67c23a">{{ wan(summary.receiptPlan) }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="stat-card">
          <div class="label">计划付款（万元）</div>
          <div class="value num" style="color:#f56c6c">{{ wan(summary.paymentPlan) }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="stat-card">
          <div class="label">净流入计划（万元）</div>
          <div class="value num" :style="{ color: netPlan < 0 ? '#f56c6c' : '#185fa5' }">{{ wan(netPlan) }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="stat-card">
          <div class="label">实际收款（万元）</div>
          <div class="value num">{{ wan(summary.receiptActual) }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="label">实际付款（万元）</div>
          <div class="value num">{{ wan(summary.paymentActual) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 收支对比图 -->
    <el-card shadow="never" class="stat-row">
      <template #header><span class="card-title">月度资金收支计划 vs 实际（万元）</span></template>
      <ChartBox :option="chartOption" height="260px" />
    </el-card>

    <!-- 项目计划表 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ month }} 各项目资金计划（元）</span>
          <el-tag size="small" effect="plain">定稿后不可被自动汇总覆盖</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="plans" stripe show-summary :summary-method="planSummary">
        <el-table-column label="项目" min-width="140">
          <template #default="{ row }">{{ row.projectName || (row.projectId ? `项目${row.projectId}` : '集团汇总') }}</template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.versionType === 'FINAL' ? 'success' : 'info'">
              {{ row.versionType === 'FINAL' ? '定稿' : '初稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划收款" align="right" width="140">
          <template #default="{ row }"><span class="num">{{ money(row.receiptPlan) }}</span></template>
        </el-table-column>
        <el-table-column label="计划付款" align="right" width="140">
          <template #default="{ row }"><span class="num">{{ money(row.paymentPlan) }}</span></template>
        </el-table-column>
        <el-table-column label="净流入计划" align="right" width="140">
          <template #default="{ row }">
            <span class="num" :style="{ color: row.netPlan < 0 ? '#f56c6c' : '' }">{{ money(row.netPlan) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实际收款" align="right" width="140">
          <template #default="{ row }"><span class="num">{{ money(row.receiptActual) }}</span></template>
        </el-table-column>
        <el-table-column label="实际付款" align="right" width="140">
          <template #default="{ row }"><span class="num">{{ money(row.paymentActual) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.versionType === 'DRAFT'" type="primary" link @click="onFinalize(row)">定稿</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 大额付款监控 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">大额付款监控（单笔 ≥ 100万，已审批/已支付）</span>
        </div>
      </template>
      <el-table :data="largePayments" stripe>
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
        <el-table-column label="审批金额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.approvedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PAID' ? 'success' : 'warning'">
              {{ row.status === 'PAID' ? '已支付' : '待支付' }}
            </el-tag>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { finalizePlan, generatePlan, monthPlan } from '../../../api/fund'
import { projectOptions } from '../../../api/fund'
import { fmtDate, money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const month = ref(dayjs().format('YYYY-MM'))
const loading = ref(false)
const generating = ref(false)
const plans = ref([])
const summary = ref({})
const largePayments = ref([])
const projects = ref([])

const projectName = id => projects.value.find(p => p.id === id)?.name || (id ? `项目${id}` : '-')
const netPlan = computed(() => Number(summary.value.netPlan || 0))

const load = async () => {
  loading.value = true
  try {
    const data = await monthPlan(month.value)
    plans.value = data.plans || []
    summary.value = data.summary || {}
    largePayments.value = data.largePayments || []
  } finally {
    loading.value = false
  }
}

const onGenerate = async () => {
  try {
    await ElMessageBox.confirm(
      `将按在建项目自动汇总生成 ${month.value} 资金计划（覆盖初稿，定稿不动），是否继续？`,
      '自动汇总',
      { type: 'warning', confirmButtonText: '生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  generating.value = true
  try {
    const r = await generatePlan(month.value)
    ElMessage.success(`生成完成：新建 ${r.created} 条，更新 ${r.updated} 条，跳过定稿 ${r.skippedFinal} 条`)
    load()
  } finally {
    generating.value = false
  }
}

const onFinalize = async row => {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.projectName || '该项目'}」${month.value} 资金计划定稿吗？定稿后自动汇总不再覆盖。`,
      '计划定稿',
      { type: 'warning', confirmButtonText: '定稿', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await finalizePlan(row.id)
  ElMessage.success('已定稿')
  load()
}

const planSummary = ({ columns }) => {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (i === 1) return ''
    const key = {
      计划收款: 'receiptPlan', 计划付款: 'paymentPlan', 净流入计划: 'netPlan',
      实际收款: 'receiptActual', 实际付款: 'paymentActual'
    }[col.label]
    if (!key) return ''
    const sum = plans.value.reduce((s, r) => s + Number(r[key] || 0), 0)
    return money(sum)
  })
}

const chartOption = computed(() => ({
  color: ['#67c23a', '#f56c6c', '#8aadd2', '#e6a23c'],
  tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
  grid: { left: 80, right: 24, top: 36, bottom: 46 },
  xAxis: { type: 'category', data: ['收款', '付款'] },
  yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
  series: [
    {
      name: '计划', type: 'bar', barWidth: 40,
      data: [Number(summary.value.receiptPlan || 0), Number(summary.value.paymentPlan || 0)],
      itemStyle: { borderRadius: [6, 6, 0, 0] }
    },
    {
      name: '实际', type: 'bar', barWidth: 40,
      data: [Number(summary.value.receiptActual || 0), Number(summary.value.paymentActual || 0)],
      itemStyle: { borderRadius: [6, 6, 0, 0] }
    }
  ]
}))

onMounted(async () => {
  projects.value = await projectOptions()
  load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 20px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
